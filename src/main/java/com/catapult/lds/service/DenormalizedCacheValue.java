package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code DenormalizedCacheValue} is a deserialized representation of a value in the denormalized cache.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DenormalizedCacheValue {

    /**
     * The object mapper used by all {@code DenormalizedCacheValue} instances
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The logger used by all {@code DenormalizedCacheValue} instances
     */
    private static final Logger logger = LoggerFactory.getLogger(DenormalizedCacheValue.class);

    /**
     * The key in the denormalized cache whose value is this denormalized cache value
     *
     * @invariant key != null
     */
    private final String resourceKey;

    /**
     * The set of connections and their subscriptions associated with the resource key
     *
     * @invariant connectionSubscriptions != null
     */
    private final Collection<ConnectionSubscriptions> connectionSubscriptions;

    /**
     * Instantiates a new {@code DenormalizedCacheValue} from the given key and connection list json string.
     *
     * @pre key != null
     * @pre jsonArrayString != null
     * @post return != null
     */
    public static DenormalizedCacheValue deserializeFromJson(String key, String connectionListJson) {
        assert key != null;
        assert connectionListJson != null;

        try {
            Collection<DenormalizedCacheValue.ConnectionSubscriptions> cs = objectMapper.readValue(connectionListJson,
                    new TypeReference<Collection<ConnectionSubscriptions>>() {
                    });
            return new DenormalizedCacheValue(key, cs);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    /**
     * Returns a json serialized version of the connection list.
     *
     * @post return != null
     */
    public String getSerializedConnectionList() {
        try {
            return objectMapper.writeValueAsString(this.connectionSubscriptions);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    /**
     * Associates the given subscription id to the given connection id in this cache value
     *
     * @pre connectionId != null
     * @pre subscriptionId != null
     */
    public void addSubscription(String connectionId, String subscriptionId) {
        assert connectionId != null;
        assert subscriptionId != null;

        this.connectionSubscriptions
                .stream()
                .filter(c -> c.connectionId.equals(connectionId))
                .findFirst()
                .ifPresentOrElse(
                        c -> c.subscriptionIds.add(subscriptionId),
                        () -> this.connectionSubscriptions.add(new ConnectionSubscriptions(connectionId,
                                new HashSet<>(Arrays.asList(subscriptionId)))));
    }

    /**
     * Dissociates the given subscription id from the given connection id in this cache value
     *
     * @pre connectionId != null
     * @pre subscriptionId != null
     */
    public void removeSubscription(String connectionId, String subscriptionId) {
        assert connectionId != null;
        assert subscriptionId != null;

        Optional<ConnectionSubscriptions> optionalConnection = this.connectionSubscriptions
                .stream()
                .filter(c -> c.connectionId.equals(connectionId))
                .findFirst();

        if (optionalConnection.isEmpty()) {
            logger.debug("Could not remove subscription {} from connection {}:  Connection not" +
                    " found.", subscriptionId, connectionId);
            return;
        }

        ConnectionSubscriptions connection = optionalConnection.get();
        if (connection.subscriptionIds.remove(subscriptionId) == false) {
            logger.debug("Could not remove subscription {} from connection {}:  Subscription " +
                    "not found.", subscriptionId, connectionId);
        }

        if (connection.subscriptionIds.isEmpty()) {
            logger.debug("Afer removing subscription {} from connection {}, connection has no " +
                    "more subscriptions associated with it.", subscriptionId, connectionId);

            this.connectionSubscriptions.remove(connection);
        }
    }

    /**
     * Returns true if there are no connections associated with this cache value, false otherwise.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return this.connectionSubscriptions.isEmpty();
    }

    /**
     * Returns the set of connection ids for this cache value.
     *
     * @post return != null
     */
    @JsonIgnore
    public Set<String> getConnectionIds() {
        return this.connectionSubscriptions.stream().map(c -> c.connectionId).collect(Collectors.toSet());
    }

    /**
     * Returns a set of subscriptions associated with the connection id for this cache value.
     *
     * @post return != null
     */
    public Set<String> getSubscriptionIds(String connectionId) {
        return this.connectionSubscriptions
                .stream()
                .filter(c -> c.connectionId.equals(connectionId))
                .findAny()
                .map(c -> c.subscriptionIds)
                .orElse(Collections.emptySet());
    }

    /**
     * Returns a map of subscription ids keyed by connection id.
     *
     * @post return != null
     */
    public Map<String, Set<String>> getSubscriptionIdsByConnectionId() {
        return this.connectionSubscriptions
                .stream()
                .collect(Collectors.toMap(ConnectionSubscriptions::getConnectionId,
                        ConnectionSubscriptions::getSubscriptionIds));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        try {
            return objectMapper.writer().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    /**
     * {@code ConnectionSubscriptions} is a container class that associates subscriptions ids with a connection.
     * <p/>
     */
    @Value
    @Builder
    @Jacksonized
    private static class ConnectionSubscriptions {
        /**
         * The connection ID associated with this denormalized connection
         */
        @NonNull
        String connectionId;

        /**
         * The set of subscription ids associated with this denormalized connection
         */
        @NonNull
        Set<String> subscriptionIds;
    }
}
