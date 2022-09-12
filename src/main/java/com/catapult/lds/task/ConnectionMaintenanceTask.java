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
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final AmazonApiGatewayManagementApiAsync apiGatewayClient;

    /**
     * If false, the task will execute the normal maintenance.  If true, the cache dump will be executed as well.
     */
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

        if (this.dumpCache) {
            taskResult.databaseDumpPreCleanup.putAll(subscriptionCacheService.dumpCache());
        }

        Set<String> connectionIds = subscriptionCacheService.getAllConnectionIds();
        this.logger.debug("cache has a record of {} open connections: {} ", connectionIds.size(), connectionIds);

        for (String connectionId : connectionIds) {
            // check to see if the connection is still valid
            GetConnectionRequest request = new GetConnectionRequest();
            request.setConnectionId(connectionId);

            Date connectedAt;

            try {
                GetConnectionResult result = apiGatewayClient.getConnection(request);
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

        // function that returns the set of all connections that are not active
        Function<Set<String>, Set<String>> deadConnectionFilter =
                x -> x.stream().filter(s -> {
                    try {
                        GetConnectionRequest request = new GetConnectionRequest();
                        request.setConnectionId(s);
                        this.apiGatewayClient.getConnection(request);
                        return false;
                    } catch (GoneException e) {
                        return true;
                    }
                }).collect(Collectors.toSet());

        subscriptionCacheService.cleanCache(deadConnectionFilter);

        if (this.dumpCache) {
            taskResult.databaseDumpPostCleanup.putAll(subscriptionCacheService.dumpCache());
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
        private Map<String, Object> databaseDumpPreCleanup = new HashMap<>();
        private Map<String, Object> databaseDumpPostCleanup = new HashMap<>();
    }
}
