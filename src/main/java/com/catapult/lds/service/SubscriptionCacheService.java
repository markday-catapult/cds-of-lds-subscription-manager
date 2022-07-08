package com.catapult.lds.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@code SubscriptionCacheService} provides methods for storing and retrieving information about subscriptions.
 */
public interface SubscriptionCacheService {

    /**
     * Returns true if the cache service is connected to the backing store, false otherwise.
     */
    public boolean isConnected();

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @throws SubscriptionException if the given connection id already exists in the cache.
     * @pre connectionId != null
     */
    public void createConnection(String connectionId) throws SubscriptionException;

    /**
     * Returns true if a connection with the given connection id exists in the cache, false otherwise.
     *
     * @pre connectionId != null
     */
    public boolean connectionExists(String connectionId);

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @throws SubscriptionException if the given connection id does not exist in the cache.
     * @pre connectionId != null
     */
    public void closeConnection(String connectionId) throws SubscriptionException;

    /**
     * Puts the given subscription into the cache.
     *
     * @throws SubscriptionException if the given subscription has a {@linkplain Subscription#getId subscription id}
     *                               that is already associated with the {@linkplain Subscription#getConnectionId
     *                               connection}.
     * @pre subscription != null
     */
    public void putSubscription(Subscription subscription) throws SubscriptionException;

    /**
     * Cancels the subscription identified by the given connection id and subscription id.
     *
     * @throws SubscriptionException if a connection with given connection id does not exist in the cache.
     * @pre subscription != null
     */
    public void cancelSubscription(String connectionId, String subscriptionId) throws SubscriptionException;

    /**
     * Returns a collection of subscriptions associated with the given connection id.
     *
     * @throws SubscriptionException if a connection with the given connection id does not exist in the cache.
     * @pre connectionId != null
     * @post return != null
     */
    public Collection<Subscription> getSubscriptions(String connectionId) throws SubscriptionException;

    /**
     * Returns the subscription associated with the given connection id and subscription id, or null if no such
     * subscription exists.
     *
     * @pre connectionId != null
     */
    public Subscription getSubscription(String connectionId, String subscriptionId);

    /**
     * Returns a map of connection Ids associated with the given criteria.  Any resource ids that did not have
     * connections associated with them will have contain an empty list
     *
     * @pre resourceIds != null
     * @post return != null
     * @post return.size() = resourceIds.size()
     */
    public Map<String, List<String>> getConnectionIdsForResourceIds(List<String> resourceIds);

}
