package com.catapult.lds.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code SubscriptionCacheService} provides methods for storing and retrieving information about subscriptions.
 */
public interface SubscriptionCacheService {

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @pre connectionId != null
     */
    public void createConnection(String connectionId);

    /**
     * Create an entry associated with the given connection id in the cache.
     *
     * @pre connectionId != null
     */
    public void closeConnection(String connectionId);

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

    /**
     * {@code Subscription} is an immutable.... TBD
     */
    public class Subscription {

        private String id = UUID.randomUUID().toString();
        private long createdAt = System.currentTimeMillis();
        private String connectionId;
        private String resourceIdsJson;

        /**
         * Returns the id of this subscription
         *
         * @post return != null
         */
        public String getId() {
            return this.id;
        }

        /**
         * Returns the connection id for this subscription
         *
         * @post return != null
         */
        public String getConnectionId() {
            return this.connectionId;
        }

        /**
         * Returns the timestamp of when this subscription was created.
         *
         * @post return > 0
         */
        public long getCreatedAt() {
            return this.createdAt;
        }

        /**
         * Returns a list of resource ids for this subscription, in stringified json format.  The resource ids will
         * already have the keyspace prefix applied to them.
         * <p/>
         * <code>["U-user-id-1", "D-device-id-3", "D-device-id-7"]</code>
         *
         * @post return != null
         */
        public String getResourceIdsJson() {
            return this.resourceIdsJson;
        }
    }
}
