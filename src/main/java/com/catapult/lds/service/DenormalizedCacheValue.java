package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code DenormalizedCacheValue} is a deserialized representation of a value in the denormalized cache.
 */
@Value
@Builder
public class DenormalizedCacheValue {

    private final String key;
    private final Collection<ConnectionSubscriptions> connectionSubscriptions;

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
                .map(c -> c.subscriptionIds.add(subscriptionId))
                .orElseGet(() -> this.connectionSubscriptions.add(new ConnectionSubscriptions(connectionId,
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
            return;
        }

        ConnectionSubscriptions connection = optionalConnection.get();
        connection.subscriptionIds.remove(subscriptionId);
        if (connection.subscriptionIds.isEmpty()) {
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
     */
    @JsonIgnore
    public Set<String> getConnectionIds() {
        return this.connectionSubscriptions.stream().map(c -> c.connectionId).collect(Collectors.toSet());
    }

    /**
     * Returns a set of subscriptions associated with the connection id for this cache value.
     */
    public Set<String> getSubscriptionIds(String connectionId) {
        return this.connectionSubscriptions
                .stream()
                .filter(c -> c.connectionId.equals(connectionId))
                .findAny()
                .map(c -> c.subscriptionIds)
                .orElse(Collections.emptySet());
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    @Value
    @Builder
    @Jacksonized
    static class ConnectionSubscriptions {
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
