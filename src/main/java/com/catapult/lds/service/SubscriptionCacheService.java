package com.catapult.lds.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@code SubscriptionCacheService} provides methods for storing and retrieving information about subscriptions.
 */
public interface SubscriptionCacheService {

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @throws SubscriptionException if the given connection id already exists in the cache.
     * @pre connectionId != null
     */
    public void createConnection(String connectionId) throws SubscriptionException;

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
     * @pre subscription != null
     */
    public void putSubscription(Subscription subscription);

    /**
     * Cancels the subscription identified by the given connection id and subscription id.
     *
     * @pre subscription != null
     */
    public void cancelSubscription(String connectionId, String subscriptionId);

    /**
     * Returns a collection of subscriptions associated with the given connection id.
     *
     * @pre connectionId != null
     * @post return != null
     */
    public Collection<Subscription> getSubscriptions(String connectionId);

    /**
     * Returns the subscription associated with the given connection id and subscription id, or null if no such
     * subscription exists.
     *
     * @pre connectionId != null
     */
    public Subscription getSubscription(String connectionId, String subscriptionId);

    /**
     * Returns a map of connection Ids associated with the given criteria.
     *
     * @pre criteria != null
     * @post return != null
     */
    public Map<String, List<String>> getConnectionIdsForResourceIds(List<String> criteria);

}
