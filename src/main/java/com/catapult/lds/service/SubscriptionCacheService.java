package com.catapult.lds.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * {@code SubscriptionCacheService} provides methods for storing and retrieving information about subscriptions.
 */
public interface SubscriptionCacheService {

    /**
     * Returns true if the cache service is connected to the backing store, false otherwise.
     */
    boolean isConnected();

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @throws SubscriptionException if the given connection id already exists in the cache.
     * @pre connectionId != null
     */
    void createConnection(String connectionId) throws SubscriptionException;

    /**
     * Returns true if a connection with the given connection id exists in the cache, false otherwise.
     *
     * @pre connectionId != null
     */
    boolean connectionExists(String connectionId);

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @throws SubscriptionException if the given connection id does not exist in the cache.
     * @pre connectionId != null
     */
    void closeConnection(String connectionId) throws SubscriptionException;

    /**
     * Puts the given subscription into the cache.
     *
     * @throws SubscriptionException if the given subscription has a {@linkplain Subscription#getId subscription id}
     *                               that is already associated with the {@linkplain Subscription#getConnectionId
     *                               connection}.
     * @pre subscription != null
     */
    void addSubscription(Subscription subscription) throws SubscriptionException;

    /**
     * Cancels the subscription identified by the given connection id and subscription id.
     *
     * @throws SubscriptionException if a connection with given connection id does not exist in the cache.
     * @pre subscription != null
     */
    void cancelSubscription(String connectionId, String subscriptionId) throws SubscriptionException;

    /**
     * Returns a collection of subscriptions associated with the given connection id.
     *
     * @throws SubscriptionException if a connection with the given connection id does not exist in the cache.
     * @pre connectionId != null
     * @post return != null
     */
    Collection<Subscription> getSubscriptions(String connectionId) throws SubscriptionException;

    /**
     * Returns the subscription associated with the given connection id and subscription id, or null if no such
     * subscription exists.
     *
     * @pre connectionId != null
     */
    Subscription getSubscription(String connectionId, String subscriptionId);

    /**
     * Returns a map of {@code denormalized cache values} associated with the given criteria.  Any resource ids that did
     * not have connections associated with it will have an {@linkplain DenormalizedCacheValue#isEmpty empty} value.
     *
     * @pre resourceIds != null
     * @post return != null
     * @post return.size() = resourceIds.size()
     */
    Map<String, DenormalizedCacheValue> getDenormalizedConnectionsForResourceIds(Set<String> resourceIds);

}

