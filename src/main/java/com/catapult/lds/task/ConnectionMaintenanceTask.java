package com.catapult.lds.task;

import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiAsync;
import com.amazonaws.services.apigatewaymanagementapi.model.GetConnectionRequest;
import com.amazonaws.services.apigatewaymanagementapi.model.GetConnectionResult;
import com.amazonaws.services.apigatewaymanagementapi.model.GoneException;
import com.catapult.lds.DisconnectHandler;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@code ConnectionMaintenanceTask} is a task that performs subscription maintenance and cleanup.  As the
 * {@linkplain DisconnectHandler disconnect handler} will not be guaranteed to be called on disconnect, this task may be
 * {@linkplain #call called} periodically to clean up the {@linkplain SubscriptionCacheService subscription cache}.
 */
@AllArgsConstructor
public class ConnectionMaintenanceTask implements Callable<ConnectionMaintenanceTask.ConnectionMaintenanceResult> {

    /**
     * The subscription cache service used by this task.
     *
     * @invariant subscriptionCacheService != null
     */
    @NonNull
    private final SubscriptionCacheService subscriptionCacheService;

    /**
     * The asynchronous api client used by this task.
     *
     * @invariant client != null
     */
    @NonNull
    private final AmazonApiGatewayManagementApiAsync client;

    private final boolean dumpCache;

    /**
     * The logger used by this task
     *
     * @invariant logger != null
     */
    @NonNull
    private final Logger logger = LoggerFactory.getLogger(ConnectionMaintenanceTask.class);

    @Override
    public ConnectionMaintenanceResult call() throws Exception {
        ConnectionMaintenanceResult taskResult = new ConnectionMaintenanceResult();

        Set<String> connectionIds = subscriptionCacheService.getAllConnectionIds();
        this.logger.debug("cache recorded open connections: {} ", connectionIds);

        for (String connectionId : connectionIds) {
            // check to see if the connection is still valid
            GetConnectionRequest request = new GetConnectionRequest();
            request.setConnectionId(connectionId);

            Date connectedAt;

            try {
                GetConnectionResult result = client.getConnection(request);
                connectedAt = result.getConnectedAt();
            } catch (GoneException e) {
                connectedAt = null;
            }

            if (connectedAt != null) {
                taskResult.preservedConnections.add(connectionId);
                this.logger.debug("connection '{}' connected at {}.", connectionId, connectedAt);
            } else {
                taskResult.cleanedUpConnections.add(connectionId);
                this.logger.debug("connection '{}' was GONE, closing connection.", connectionId);
                try {
                    subscriptionCacheService.closeConnection(connectionId);
                } catch (SubscriptionException e) {
                    // fail and continue cleanup if the connection was not found in the cache.
                    this.logger.error("error trying to clean up the connection '" + connectionId + "'", e);
                }
            }
        }

        if (dumpCache) {
            taskResult.databaseDump.putAll(subscriptionCacheService.dumpCache());
        }

        this.logger.debug("{} connections preserved: {}",
                taskResult.preservedConnections.size(),
                taskResult.preservedConnections);
        this.logger.debug("{} connections cleaned up: {}",
                taskResult.cleanedUpConnections.size(),
                taskResult.cleanedUpConnections);

        return taskResult;
    }

    @Value
    @Jacksonized
    public static class ConnectionMaintenanceResult {
        private Collection<String> preservedConnections = new HashSet<>();
        private Collection<String> cleanedUpConnections = new HashSet<>();
        private Map<String, Object> databaseDump = new HashMap<>();
    }
}
