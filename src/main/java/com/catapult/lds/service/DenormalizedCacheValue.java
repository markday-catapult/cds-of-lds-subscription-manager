package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code DenormalizedCacheValue} is a deserialized representation of a value in the denormalized cache.
 */
@Value
@Jacksonized
@Builder
@Slf4j
public class DenormalizedCacheValue {

    /**
     * The object mapper used by all {@code DenormalizedCacheValue} instances
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The key in the denormalized cache whose value is this denormalized cache value
     *
     * @invariant key != null
     */
    @NonNull
    String key;

    /**
     * The set of connections and their subscriptions associated with the resource key
     *
     * @invariant connectionSubscriptions != null
     */
    @NonNull
    @JsonProperty("connections")
    Set<DenormalizedCacheConnection> denormalizedCacheConnections;

    public static DenormalizedCacheValue fromJson(String jsonString) {
        assert jsonString != null;

        try {
            return objectMapper.readValue(jsonString, DenormalizedCacheValue.class);
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize DenormalizedCacheValue", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the json serialized form of this denormalized cache value
     *
     * @throws RuntimeException if an issue arises serializing this denormalized cache value to json.
     * @post return != null
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize DenormalizedCacheValue", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds the given subscription to appropriate connection in this cache value
     *
     * @pre subscription != null
     */
    public void addSubscription(Subscription subscription) {
        assert subscription != null;

        SimplifiedSubscription simplifiedSubscription = SimplifiedSubscription.builder()
                .id(subscription.getId())
                .sampleRate(subscription.getSampleRate())
                .build();

        this.denormalizedCacheConnections
                .stream()
                .filter(c -> c.getId().equals(subscription.getConnectionId()))
                .findFirst()
                .ifPresentOrElse(
                        c -> c.simplifiedSubscriptions.add(simplifiedSubscription),
                        () -> this.denormalizedCacheConnections.add(
                                DenormalizedCacheConnection.builder()
                                        .id(subscription.getConnectionId())
                                        .simplifiedSubscriptions(new HashSet<>(Set.of(simplifiedSubscription)))
                                        .build()
                        ));
    }

    /**
     * Dissociates the all the subscriptions for the given connection id in this cache value
     *
     * @pre connectionId != null
     */
    public void removeConnection(String connectionId) {
        assert connectionId != null;

        Set<DenormalizedCacheConnection> connections = this.denormalizedCacheConnections
                .stream()
                .filter(c -> c.getId().equals(connectionId))
                .collect(Collectors.toSet());

        if (connections.isEmpty()) {
            log.debug("Could not remove connection {}:  Connection not found.", connectionId);
            return;
        }

        this.denormalizedCacheConnections.removeAll(connections);
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

        Optional<DenormalizedCacheConnection> optionalConnection = this.denormalizedCacheConnections
                .stream()
                .filter(c -> c.getId().equals(connectionId))
                .findFirst();

        if (optionalConnection.isEmpty()) {
            log.debug("Could not remove subscription {} from connection {}:  Connection not" +
                    " found.", subscriptionId, connectionId);
            return;
        }

        DenormalizedCacheConnection connection = optionalConnection.get();

        // Temporarily remove the connection from the set as we can't remove it from the set once it's mutated
        this.denormalizedCacheConnections.remove(connection);

        connection.getSimplifiedSubscriptions()
                .stream()
                .filter(s -> s.id.equals(subscriptionId))
                .findAny()
                .ifPresentOrElse(
                        s -> connection.getSimplifiedSubscriptions().remove(s),
                        () -> log.debug("Could not remove subscription {} from connection {}:  Subscription " +
                                "not found.", subscriptionId, connectionId)
                );

        // Add the connection back to the set if there are more subscriptions
        if (!connection.getSimplifiedSubscriptions().isEmpty()) {
            log.info("After removing subscription {} from connection {}, connection has no " +
                    "more subscriptions associated with it.", subscriptionId, connectionId);

            this.denormalizedCacheConnections.add(connection);
        }
    }

    /**
     * Returns true if there are no connections associated with this cache value, false otherwise.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return this.denormalizedCacheConnections.isEmpty();
    }

    /**
     * Returns the set of connection ids for this cache value.
     *
     * @post return != null
     */
    @JsonIgnore
    public Set<String> getConnectionIds() {
        return this.denormalizedCacheConnections
                .stream()
                .map(DenormalizedCacheConnection::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a set of subscriptions associated with the connection id for this cache value.
     *
     * @post return != null
     */
    @JsonIgnore
    public Set<String> getSubscriptionIds(String connectionId) {
        return this.denormalizedCacheConnections
                .stream()
                .filter(c -> c.getId().equals(connectionId))
                .findAny()
                .map(DenormalizedCacheConnection::getSubscriptionIds)
                .orElse(Collections.emptySet());
    }

    /**
     * Returns a map of subscription ids keyed by connection id.
     *
     * @post return != null
     */
    @JsonIgnore
    public Map<String, Set<String>> getSubscriptionIdsByConnectionId() {
        return this.denormalizedCacheConnections
                .stream()
                .collect(Collectors.toMap(DenormalizedCacheConnection::getId,
                        DenormalizedCacheConnection::getSubscriptionIds));
    }

    /**
     * {@code DenormalizedCacheConnection} is a container class that associates subscriptions ids with a connection.
     */
    @Value
    @Builder
    @Jacksonized
    private static class DenormalizedCacheConnection {
        /**
         * The ID of this connection
         */
        @NonNull
        String id;

        /**
         * The set of simplified subscriptions associated with this denormalized connection
         */
        @JsonProperty("subscriptions")
        @NonNull
        Set<SimplifiedSubscription> simplifiedSubscriptions;

        Set<String> getSubscriptionIds() {
            return this.simplifiedSubscriptions.stream().map(s -> s.id).collect(Collectors.toSet());
        }
    }

    @Value
    @Builder
    @Jacksonized
    private static class SimplifiedSubscription {

        /**
         * The id of this subscription
         */
        @NonNull
        String id;

        /**
         * The sample rate for this subscription .  If null, do not apply the sample rate.
         */
        Integer sampleRate;

    }
}
