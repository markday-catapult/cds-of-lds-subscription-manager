package com.catapult.lds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code RedisSubscriptionCacheService} is an implementation of {@code SubscriptionCacheService} that is backed by a
 * redis instance.
 */
public class RedisSubscriptionCacheService implements SubscriptionCacheService {

    /**
     * The singleton instance of the redis subscription cache service
     *
     * @invariant instance != null
     */
    public static final SubscriptionCacheService instance = new RedisSubscriptionCacheService();

    /**
     * The name of the environment variable which has a value of the url of the redis cluster.
     */
    public static final String LDS_REDIS_HOST_ENV = "LDS_REDIS_HOST";

    /**
     * The name of the environment variable which has a value of the url of the redis cluster port.
     */
    public static final String LDS_REDIS_PORT_ENV = "LDS_REDIS_PORT";

    /**
     * The name of the key which has a value of the timestamp that a hash value was created at.
     */
    private static final String CREATED_AT = "created_at";

    /**
     * The object mapper used by this service.
     *
     * @invariant objectMapper != null
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The namespace for a connection key
     */
    private static final String CONNECTION_NAMESPACE = "$connection-id-";

    /**
     * The key for the set of known open connection ids.  The values stored in this set are connection ids, and not
     * connection keys.
     */
    private static final String CONNECTIONS_KEY = "$connections";

    /**
     * Connection to AWS Elasticache redis cluster
     *
     * @invariant redisClient != null
     */
    private final StatefulRedisConnection<String, String> redisClient;

    /**
     * The logger used by this cache service.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(RedisSubscriptionCacheService.class);

    private RedisSubscriptionCacheService() {

        String host = System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV);
        String port = System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV);
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));

        this.redisClient = RedisClient.create(redisURI).connect();
    }

    /**
     * Helper method that converts the given set of strings to a stringified json array.
     *
     * @pre set != null
     * @post return != null
     */
    static String setToJsonString(Set<String> set) {
        assert set != null;

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
    public boolean isConnected() {
        return this.redisClient.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createConnection(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        this.logger.debug("creating connection '{}' ", connectionId);

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = this.connectionIdToKey(connectionId);

        if( ! syncCommands.smembers(CONNECTIONS_KEY).contains(connectionId)){
            syncCommands.multi();
            syncCommands.sadd(CONNECTIONS_KEY,connectionId);
            syncCommands.hsetnx(connectionKey, CREATED_AT, "" + System.currentTimeMillis());
            syncCommands.exec();
        }else{
            throw new SubscriptionException(String.format("Connection '%s' already exists in the cache.",
                    connectionId));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getAllConnectionIds() {
        return this.redisClient.sync().smembers(CONNECTIONS_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeConnection(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        this.logger.debug("closing connection {} ", connectionId);

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = this.connectionIdToKey(connectionId);
        if (syncCommands.exists(connectionKey) == 0) {
            throw new SubscriptionException(String.format("Connection '%s' does not exist in the cache.",
                    connectionId));
        }

        Set<String> remainingSubscriptionIds = syncCommands.hkeys(connectionKey)
                .stream()
                .filter(k -> !CREATED_AT.equals(k))
                .collect(Collectors.toSet());

        this.logger.debug("removing all remaining subscriptions {} ", remainingSubscriptionIds);

        remainingSubscriptionIds.forEach(k -> this.cancelSubscriptionInternal(connectionId, k)); // cancel all remaining subscriptions

        syncCommands.del(connectionKey);

        // remove the connection id from the set of open connections
        syncCommands.srem(CONNECTIONS_KEY, connectionId);

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
            this.cancelSubscription(connectionId, subscriptionId);
        } catch (SubscriptionException e) {
            // ignore this exception
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSubscription(Subscription subscription) throws SubscriptionException {
        assert subscription != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionId = subscription.getConnectionId();
        String subscriptionId = subscription.getId();
        String connectionKey = this.connectionIdToKey(connectionId);

        this.logger.debug("creating subscription '{}' for connection '{}'", subscriptionId, connectionId);

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
        Map<String, DenormalizedCacheValue> connectionsByResourceId =
                this.getDenormalizedConnectionsForResourceIds(subscription.getResources());

        // add the subscription to each of the denormalized cache values:
        connectionsByResourceId.values().forEach(v -> v.addSubscription(connectionId, subscriptionId));

        // store the updated denormalized cache
        Map<String, String> request = connectionsByResourceId
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        kv -> kv.getKey(),
                        kv -> kv.getValue().getSerializedConnectionList())
                );

        // if there are name-spaced resources that were not initially associated with a connection, add them now.
        if (connectionsByResourceId.size() > 0) {
            syncCommands.mset(request);
        }

        this.logger.debug("sending request to cache: {}", request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelSubscription(String connectionId, String subscriptionId) throws SubscriptionException {
        assert connectionId != null;
        assert subscriptionId != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = this.connectionIdToKey(connectionId);

        this.logger.debug("cancelling subscription '{}' for connection '{}'", subscriptionId, connectionId);

        // adjust denormalized cache here

        Subscription subscription = this.getSubscription(connectionId, subscriptionId);

        // no work to do if subscription cannot be found
        if (subscription == null) {
            return;
        }

        Set<String> resources = subscription.getResources();

        if (!resources.isEmpty()) {
            Map<String, DenormalizedCacheValue> denormalizedCacheValuesByResourceId =
                    syncCommands.mget(resources.toArray(String[]::new))
                            .stream()
                            .collect(Collectors.toMap(
                                    kv -> kv.getKey(),
                                    kv -> DenormalizedCacheValue.deserializeFromJson(kv.getKey(), kv.getValue())));

            this.logger.trace("denormalizedCacheValuesByResourceId: {}", denormalizedCacheValuesByResourceId);

            denormalizedCacheValuesByResourceId.values()
                    .forEach(v -> v.removeSubscription(connectionId, subscriptionId));

            this.logger.trace("resources: {} ", denormalizedCacheValuesByResourceId);

            // get the set of empty resources to delete
            Set<String> resourcesToDelete = denormalizedCacheValuesByResourceId.entrySet()
                    .stream()
                    .filter(es -> es.getValue().isEmpty())
                    .map(es -> es.getKey())
                    .collect(Collectors.toSet());

            this.logger.trace("resourcesToDelete: {} ", resourcesToDelete);

            // build the map of modified resources
            Map<String, String> resourcesToModify =
                    denormalizedCacheValuesByResourceId.entrySet()
                            .stream()
                            .filter(es -> !resourcesToDelete.contains(es.getKey()))
                            .collect(Collectors.toMap(
                                    es -> es.getKey(),
                                    es -> es.getValue().getSerializedConnectionList()));

            // remove denormalized connections from a device if the list of subscriptions is empty

            this.logger.trace("modifiedResources: {} " + resourcesToModify);

            // delete resource cache
            if (resourcesToDelete.size() > 0) {
                syncCommands.del(resourcesToDelete.toArray(String[]::new));
            }

            // modify resource cache entries
            if (resourcesToModify.size() > 0) {
                syncCommands.mset(resourcesToModify);
            }

        }

        syncCommands.hdel(connectionKey, subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Subscription> getSubscriptions(String connectionId) throws SubscriptionException {
        assert connectionId != null;

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = this.connectionIdToKey(connectionId);

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

        String connectionKey = this.connectionIdToKey(connectionId);
        String resourceJson = syncCommands.hget(connectionKey, subscriptionId);

        return resourceJson == null ? null : new Subscription(connectionId, subscriptionId, resourceJson);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, DenormalizedCacheValue> getDenormalizedConnectionsForResourceIds(Set<String> resourceIds) {
        assert resourceIds != null;

        if (resourceIds.isEmpty()) return Collections.emptyMap();

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        Map<String, DenormalizedCacheValue> connectionsByResourceId =
                syncCommands.mget(resourceIds.toArray(String[]::new))
                        .stream()
                        .collect(Collectors.toMap(
                                kv -> kv.getKey(),
                                kv -> kv.isEmpty() ?
                                        DenormalizedCacheValue.deserializeFromJson(kv.getKey(), "[]") :
                                        DenormalizedCacheValue.deserializeFromJson(kv.getKey(), kv.getValue()))
                        );

        assert resourceIds.size() == connectionsByResourceId.size();

        return connectionsByResourceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> dumpCache() {
        Set<String> keys = new HashSet<>(redisClient.sync().keys("*"));

        Map<String, Object> cache = new HashMap<>();
        for (String k : keys) {
            String type = redisClient.sync().type(k);
            if (type.equals("string")) cache.put(k, redisClient.sync().get(k));
            else if (type.equals("set")) cache.put(k, redisClient.sync().smembers(k));
            else cache.put(k, redisClient.sync().hgetall(k));
        }

        return cache;
    }

    /**
     * {@inheritDoc}
     */
    public void cleanCache(Function<Set<String>, Set<String>> deadConnectionFilter) {
        assert deadConnectionFilter != null;

        this.logger.info("cleaning cache");

        var denormalizedCacheValuesByKey =
                getDenormalizedConnectionsForResourceIds(this.dumpCache().keySet().stream().filter(s -> !s.startsWith(
                        "$connection")).collect(Collectors.toSet()));

        Set<String> denormalizedCacheEntriesToRemove = new HashSet<>();

        for (var entry : denormalizedCacheValuesByKey.entrySet()) {
            String resourceId = entry.getKey();
            DenormalizedCacheValue denormalizedCacheValue = entry.getValue();

            var connectionIds = denormalizedCacheValue.getConnectionIds();
            var deadConnectionIds = deadConnectionFilter.apply(connectionIds);

            this.logger.debug("Got {} total connections, {} of which are dead.  Removing {} from {} ",
                    connectionIds.size(),
                    deadConnectionIds.size(),
                    deadConnectionIds,
                    connectionIds);

            for (String deadConnection : deadConnectionIds) {
                denormalizedCacheValue.removeConnection(deadConnection);
            }
        }

        Map<String, String> resourcesToModify =
                denormalizedCacheValuesByKey.entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                es -> es.getKey(),
                                es -> es.getValue().getSerializedConnectionList()));

        // remove denormalized connections from a device if the list of subscriptions is empty
        this.logger.debug("modifiedResources: {} " + resourcesToModify);

        // modify resource cache entries
        if (resourcesToModify.size() > 0) {
            this.redisClient.sync().mset(resourcesToModify);
        }
    }

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
