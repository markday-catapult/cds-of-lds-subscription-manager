package com.catapult.lds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * {@code Connection} is the top level object in the normalized cache.
 */
@Value
@Jacksonized
@Builder
@Slf4j
class Connection {

    /**
     * The object mapper used by this class.
     *
     * @invariant objectMapper != null
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The id of this connection.
     *
     * @invariant id != null
     */
    @NonNull
    String id;

    /**
     * The at which this connection was created at in milliseconds.
     *
     * @invariant createdAt > 0
     */
    @NonNull
    @Builder.Default
    Long createdAt = System.currentTimeMillis();

    /**
     * The openfield user id associated with the owner of this connection.
     *
     * @invariant subscriberId != null
     */
    @NonNull
    String subscriberId;

    /**
     * The set of subscriptions associated with this connection.
     *
     * @invariant subscriptions != null
     */
    @NonNull
    @Builder.Default
    Set<Subscription> subscriptions = new HashSet<>();

    /**
     * Returns a new {@code Connection} from the given json string.
     *
     * @throws SubscriptionException if an issue arises deserializing the given JSON
     * @pre jsonString != null
     * @post return != null
     */
    public static Connection fromJson(String jsonString) throws SubscriptionException {
        assert jsonString != null;

        try {
            return objectMapper.readValue(jsonString, Connection.class);
        } catch (JsonProcessingException e) {
            log.error("could not deserialize Connection", e);
            throw new SubscriptionException(e);
        }
    }

    /**
     * Returns the json serialized form of this connection
     *
     * @throws SubscriptionException if an issue arises serializing this connection to json.
     * @post return != null
     */
    public String toJson() throws SubscriptionException {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.error("could not serialize Connection", e);
            throw new SubscriptionException(e);
        }
    }

    /**
     * Associates the given subscription with this connection.
     *
     * @pre subscription != null
     */
    public void addSubscription(Subscription subscription) {
        assert subscription != null;

        this.subscriptions.add(subscription);
    }

    /**
     * Returns the subscription identified by the subscription id, or null if is not present in
     * {@linkplain #getSubscriptions the set of subscriptions}.
     *
     * @pre subscriptionId != null
     */
    public Subscription findSubscription(String subscriptionId) {
        assert subscriptionId != null;

        return this.subscriptions.stream()
                .filter(s -> subscriptionId.equals(s.getId()))
                .findFirst()
                .orElse(null);

    }

    /**
     * Removes the subscription identified by the subscription id and returns it, or null if is not present in
     * {@linkplain #getSubscriptions the set of subscriptions}.
     *
     * @pre subscriptionId != null
     */
    public Subscription removeSubscription(String subscriptionId) {
        Subscription subscription = this.findSubscription(subscriptionId);
        if (subscription != null) {
            this.subscriptions.remove(subscription);
        }

        return subscription;
    }
}
