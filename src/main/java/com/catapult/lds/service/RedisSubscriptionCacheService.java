package com.catapult.lds.service;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class RedisSubscriptionCacheService implements SubscriptionCacheService {

    public static SubscriptionCacheService instance = new RedisSubscriptionCacheService();

    /**
     * The name of the environment variable which has a value of the url of the redis cluster.
     */
    public final static String CLUSTER_ENV_NAME = "LDS_REDIS_CLUSTER_HOST";

    /**
     * The name of the environment variable which has a value of the url of the redis cluster port.
     */
    public final static String CLUSTER_ENV_PORT = "LDS_REDIS_CLUSTER_PORT";

    /**
     * The name of the key which has a value of the timestamp that a hash value was created at.
     */
    private final static String CREATED_AT = "created_at";

    /**
     * Connection to AWS Elasticache redis cluster
     *
     * @invariant redisClient != null
     */
    private final StatefulRedisClusterConnection<String, String> redisClient;

    private RedisSubscriptionCacheService() {

        String host = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_NAME);
        String port = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_PORT);
        RedisURI redisURI = RedisURI.create(host, Integer.valueOf(port));

        this.redisClient = RedisClusterClient.create(redisURI).connect();
    }

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(RedisSubscriptionCacheService.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void createConnection(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        RedisAdvancedClusterCommands<String, String> syncCommands = this.redisClient.sync();
        boolean success = syncCommands.hsetnx(connectionId, CREATED_AT, new Date().toString());

        if (!success) {
            throw new SubscriptionException(String.format("Connection '%s' already exists in the cache.",
                    connectionId));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeConnection(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        RedisAdvancedClusterCommands<String, String> syncCommands = this.redisClient.sync();

        if (syncCommands.exists(connectionId) == 0) {
            throw new SubscriptionException(String.format("Connection '%s' does not exist in the cache.",
                    connectionId));
        }

        syncCommands.hkeys(connectionId)
                .stream()
                .filter(k -> !CREATED_AT.equals(k)) // don't include the created_at key
                .forEach(k -> cancelSubscription(connectionId, k)); // cancel all remaining subscriptions

        syncCommands.del(connectionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSubscription(Subscription subscription) throws SubscriptionException {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelSubscription(String connectionId, String subscriptionId) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Subscription> getSubscriptions(String connectionId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Subscription getSubscription(String connectionId, String subscriptionId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> getConnectionIdsForResourceIds(java.util.List<String> criteria) {
        return null;
    }
}
