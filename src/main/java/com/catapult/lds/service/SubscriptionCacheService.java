package com.catapult.lds.service;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * {@code SubscriptionCacheService} provides methods for storing and retrieving information about subscriptions.
 */
public interface SubscriptionCacheService {

    /**
     * Returns true if the cache service is connected to the backing store, false otherwise.
     */
    boolean isConnected();

    /**
     * Create a new entry associated with the given connection id and subscriber id in the cache.
     *
     * @throws SubscriptionException if the given connection id already exists in the cache.
     * @pre connectionId != null
     * @pre subscriberId != null
     */
    void createConnection(String connectionId, String subscriberId) throws SubscriptionException;

    /**
     * Returns the set of all open connection ids.
     *
     * @post return != null;
     */
    Set<String> getAllConnectionIds();

    /**
     * Returns the connection associated with the given connection id.
     *
     * @throws SubscriptionException if the given connection id does not exist in the cache.
     * @pre connectionId != null
     */
    Connection getConnection(String connectionId) throws SubscriptionException;

    /**
     * Removes all subscriptions associated with this connection from the deserialized cache, and removes the entry
     * associated with this connection from the normalized cache.
     *
     * @throws SubscriptionException if the given connection id does not exist in the cache.
     * @pre connectionId != null
     */
    void closeConnection(String connectionId) throws SubscriptionException;

    /**
     * Puts the given subscription into the cache.
     *
     * @throws SubscriptionException if the given subscription has a {@linkplain Subscription#getId subscription id}
     *                               that is already associated with the
     *                               {@linkplain Subscription#getConnectionId connection}.
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
     * Returns a map of {@code denormalized cache values} associated with the given criteria.  Any resource ids that did
     * not have connections associated with it will have an {@linkplain DenormalizedCacheValue#isEmpty empty} value.
     *
     * @pre resourceIds != null
     * @post return != null
     * @post return.size() = resourceIds.size()
     */
    Map<String, DenormalizedCacheValue> getDenormalizedConnectionsForResourceIds(Set<String> resourceIds);

    /**
     * Returns the entire cache.  This method is not guaranteed to be performant, and must only be called for
     * maintenance purposes.
     *
     * @post return != null
     */
    Map<String, Object> dumpCache();

    /**
     * Cleans the denormalized cache.  This method is not guaranteed to be performant, and must only be called for
     * maintenance purposes.
     *
     * @param deadConnectionFilter is a filter that takes a list of connection ids and returns only the connection ids
     *                             that are dead
     *
     * @pre deadConnectionFilter != null
     */
    void cleanCache(Function<Set<String>, Set<String>> deadConnectionFilter);
}

