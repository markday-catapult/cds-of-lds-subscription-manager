package com.catapult.lds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.sync.RedisHashCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisSubscriptionCacheService implements SubscriptionCacheService {

    public static SubscriptionCacheService instance = new RedisSubscriptionCacheService();

    /**
     * The name of the environment variable which has a value of the url of the redis cluster.
     */
    public final static String CLUSTER_ENV_NAME = "LDS_REDIS_HOST";

    /**
     * The name of the environment variable which has a value of the url of the redis cluster port.
     */
    public final static String CLUSTER_ENV_PORT = "LDS_REDIS_PORT";

    /**
     * The name of the key which has a value of the timestamp that a hash value was created at.
     */
    private final static String CREATED_AT = "created_at";

    private final static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Connection to AWS Elasticache redis cluster
     *
     * @invariant redisClient != null
     */
    private final StatefulRedisConnection<String, String> redisClient;

    /**
     * The logger used by all instances of this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(RedisSubscriptionCacheService.class);

    private RedisSubscriptionCacheService() {

        String host = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_NAME);
        String port = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_PORT);
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));

        this.redisClient = RedisClient.create(redisURI).connect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return this.redisClient.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createConnection(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        this.logger.info("creating connection '{}' ", connectionId);

        RedisHashCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = connectionIdToKey(connectionId);
        boolean success = syncCommands.hsetnx(connectionKey, CREATED_AT, "" + System.currentTimeMillis());

        if (!success) {
            throw new SubscriptionException(String.format("Connection '%s' already exists in the cache.",
                    connectionId));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectionExists(String connectionId) {
        assert connectionId != null;
        String connectionKey = connectionIdToKey(connectionId);

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        return syncCommands.exists(connectionKey) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeConnection(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        this.logger.info("closing connection {} ", connectionId);

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = connectionIdToKey(connectionId);
        if (syncCommands.exists(connectionKey) == 0) {
            throw new SubscriptionException(String.format("Connection '%s' does not exist in the cache.",
                    connectionId));
        }

        Set<String> remainingSubscriptionIds = syncCommands.hkeys(connectionKey)
                .stream()
                .filter(k -> !CREATED_AT.equals(k))
                .collect(Collectors.toSet());

        this.logger.info("removing all remaining subscriptions {} ", remainingSubscriptionIds);

        remainingSubscriptionIds.forEach(k -> cancelSubscriptionInternal(connectionId, k)); // cancel all remaining subscriptions

        syncCommands.del(connectionKey);
    }

    /**
     * {@linkplain #cancelSubscription Cancels} the subscription identified by the given connection id and subscription
     * id, suppressing the exception when a connection is not found.
     *
     * @pre connectionId != null
     * @pre subscriptionId != null
     */
    private void cancelSubscriptionInternal(String connectionId, String subscriptionId) {
        try {
            cancelSubscription(connectionId, subscriptionId);
        } catch (SubscriptionException e) {
            // ignore this exception
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSubscription(Subscription subscription) throws SubscriptionException {
        assert subscription != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionId = subscription.getConnectionId();
        String subscriptionId = subscription.getId();
        String connectionKey = connectionIdToKey(connectionId);

        this.logger.info("creating subscription '{}' for connection '{}'", subscriptionId, connectionId);

        if (syncCommands.exists(connectionKey) == 0) {
            throw new SubscriptionException(String.format("Connection '%s' does not exist in the cache.",
                    connectionId));
        }
        if (syncCommands.hexists(connectionKey, subscriptionId)) {
            throw new SubscriptionException(String.format("subscription '%s' for connection `%s` already exists in " +
                    "the cache.", subscriptionId, connectionId));
        }

        // add subscription to connection hash
        syncCommands.hset(connectionKey, subscriptionId, setToJsonString(subscription.getResources()));

        // DENORMALIZED CACHE UPDATE

        // get connections for all resources in the new subscription
        Map<String, Set<String>> connectionsByResourceId = getConnectionIdsForResourceIds(subscription.getResources());

        // add the connection id to the list of connections
        connectionsByResourceId.values()
                .stream()
                .filter(v -> !v.contains(connectionId))
                .forEach(v -> v.add(connectionId));

        Map<String, String> request = connectionsByResourceId
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        kv -> kv.getKey(),
                        kv -> setToJsonString(kv.getValue()))
                );

        // if there are name-spaced resources that were not initially associated with a connection, add them now.
        if (connectionsByResourceId.size() > 0) {
            syncCommands.mset(request);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelSubscription(String connectionId, String subscriptionId) throws SubscriptionException {
        assert connectionId != null;
        assert subscriptionId != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = connectionIdToKey(connectionId);

        this.logger.info("cancelling subscription '{}' for connection '{}'", subscriptionId, connectionId);

        // adjust denormalized cache here

        // get all resources for this connection we need to keep
        Set<String> resourcesToKeep = this.getSubscriptions(connectionId)
                .stream()
                .filter(s -> !s.getId().equals(subscriptionId))
                .flatMap(s -> s.getResources().stream())
                .collect(Collectors.toSet());

        this.logger.info("resources to keep: {}", resourcesToKeep);

        // get all resources for this subscription and subtract the resources we need to keep
        Subscription subscriptionToRemove = this.getSubscription(connectionId, subscriptionId);
        Set<String> resourcesToDisassociate = subscriptionToRemove.getResources();
        resourcesToDisassociate.removeAll(resourcesToKeep);

        this.logger.info("resources to disassociate: {}", resourcesToDisassociate);

        // if there are resources that are only associated to this connection by the subscription to delete, remove
        // the connection from those resources.
        if (!resourcesToDisassociate.isEmpty()) {
            // get key value pairs for name-spaced resources (ie. "user-<user-id>" or "athlete-<athlete-id>")
            Map<String, String> modifiedConnectionJsonListByResourceId =
                    syncCommands.mget(resourcesToDisassociate.toArray(String[]::new)).stream()
                            .map(kv -> KeyValue.just(kv.getKey(), jsonStringToSet(kv.getValue())))
                            .filter(kv -> kv.getValue().remove(connectionId) && !kv.getValue().isEmpty())
                            .collect(Collectors.toMap(
                                    kv -> kv.getKey(),
                                    kv -> setToJsonString(kv.getValue())));

            this.logger.info("modifiedConnectionJsonListByResourceId: {}", modifiedConnectionJsonListByResourceId);

            List<String> resourcesWithoutConnections = new ArrayList<>(resourcesToDisassociate);

            resourcesWithoutConnections.removeAll(modifiedConnectionJsonListByResourceId.keySet());
            this.logger.info("resources without connections: {}", resourcesWithoutConnections);

            // delete resource cache
            if (resourcesWithoutConnections.size() > 0) {
                syncCommands.del(resourcesWithoutConnections.toArray(String[]::new));
            }
            
            // modify resource cache entries
            if (modifiedConnectionJsonListByResourceId.size() > 0) {
                syncCommands.mset(modifiedConnectionJsonListByResourceId);
            }
        }

        syncCommands.hdel(connectionKey, subscriptionId);
    }

    Set<String> jsonStringToSet(String jsonArrayString) {
        try {
            return objectMapper.readValue(jsonArrayString, new TypeReference<HashSet<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    String setToJsonString(Set<String> set) {
        try {
            return objectMapper.writeValueAsString(set);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Subscription> getSubscriptions(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = connectionIdToKey(connectionId);

        Map<String, String> resourceListsBySubscriptionId = syncCommands.hgetall(connectionKey);
        if (resourceListsBySubscriptionId.isEmpty()) {
            throw new SubscriptionException(String.format("Connection '%s' does not exist in the cache", connectionId));
        }

        return resourceListsBySubscriptionId.entrySet()
                .stream()
                .filter(e -> !CREATED_AT.equals(e.getKey()))
                .map(e -> new Subscription(connectionId, e.getKey(), e.getValue()))
                .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Subscription getSubscription(String connectionId, String subscriptionId) {
        assert connectionId != null;
        assert subscriptionId != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = connectionIdToKey(connectionId);
        String resourceJson = syncCommands.hget(connectionKey, subscriptionId);

        return resourceJson == null ? null : new Subscription(connectionId, subscriptionId, resourceJson);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Set<String>> getConnectionIdsForResourceIds(Set<String> resourceIds) {
        assert resourceIds != null;

        if (resourceIds.isEmpty()) return Collections.emptyMap();

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        Map<String, Set<String>> connectionsByResourceId =
                syncCommands.mget(resourceIds.toArray(String[]::new))
                        .stream()
                        .collect(Collectors.toMap(
                                kv -> kv.getKey(),
                                kv -> kv.isEmpty() ? new HashSet<>() : jsonStringToSet(kv.getValue()))
                        );

        assert resourceIds.size() == connectionsByResourceId.size();

        return connectionsByResourceId;
    }

    private final static String CONNECTION_NAMESPACE = "$connection-id-";

    /**
     * Returns the key of the given connection id
     *
     * @pre connectionId != null
     * @post return != null
     */
    private String connectionIdToKey(String connectionId) {
        assert connectionId != null;
        return CONNECTION_NAMESPACE + connectionId;
    }
}
