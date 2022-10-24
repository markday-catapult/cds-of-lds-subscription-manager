package com.catapult.lds.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * The namespace for a connection key
     */
    private static final String CONNECTION_NAMESPACE = "$connection-id-";

    /**
     * The key for the set of known open connection ids.  The values stored in this set are connection ids, and not
     * connection keys.
     */
    private static final String CONNECTIONS_KEY = "$connections";

    /**
     * Connection to a redis cluster
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
    public void createConnection(String connectionId, String subscriberId) throws SubscriptionException {
        assert connectionId != null;

        this.logger.debug("creating connection '{}' ", connectionId);
        Connection newConnection = Connection.builder()
                .id(connectionId)
                .subscriberId(subscriberId)
                .build();

        this.logger.debug("persisting connection '{}' ", connectionId);

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = this.connectionIdToKey(connectionId);

        // make sure to track connections first, in order to troubleshoot dead connections later.
        final boolean success = syncCommands.sadd(CONNECTIONS_KEY, connectionId) > 0;

        if (!success) {
            throw new SubscriptionException(String.format("Connection '%s' already exists in the cache.",
                    connectionId));
        }

        syncCommands.setnx(connectionKey, newConnection.toJson());
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
    public Connection getConnection(String connectionId) throws SubscriptionException {

        RedisCommands<String, String> syncCommands = this.redisClient.sync();

        String connectionKey = this.connectionIdToKey(connectionId);

        String serializedConnection = syncCommands.get(connectionKey);

        if (serializedConnection == null) {
            throw new SubscriptionException(String.format("Connection '%s' not found in cache", connectionKey));
        }

        return Connection.fromJson(serializedConnection);
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

        Connection connection = Connection.fromJson(syncCommands.get(connectionKey));

        Set<String> remainingSubscriptionIds = connection.getSubscriptions()
                .stream()
                .map(Subscription::getId)
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
        String connectionKey = this.connectionIdToKey(connectionId);

        this.logger.debug("creating subscription '{}' for connection '{}'", subscription.getConnectionId(), connectionId);

        if (syncCommands.exists(connectionKey) == 0) {
            throw new SubscriptionException(String.format("Connection '%s' does not exist in the cache.",
                    connectionId));
        }

        Connection connection = this.getConnection(connectionId);

        if (connection.findSubscription(subscription.getId()) != null) {
            throw new SubscriptionException(String.format("subscription '%s' for connection `%s` already exists in " +
                    "the cache.", subscription.getId(), connectionId));
        }

        // add subscription to connection hash
        connection.addSubscription(subscription);
        syncCommands.set(connectionKey, connection.toJson());

        // DENORMALIZED CACHE UPDATE

        // get connections for all resources in the new subscription
        Map<String, DenormalizedCacheValue> connectionsByResourceId =
                this.getDenormalizedConnectionsForResourceIds(subscription.getResources());

        // add the subscription to each of the denormalized cache values:
        connectionsByResourceId.values().forEach(v -> v.addSubscription(subscription));

        // store the updated denormalized cache
        Map<String, String> request = connectionsByResourceId
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        kv -> kv.getKey(),
                        kv -> kv.getValue().toJson())
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

        Connection connection = this.getConnection(connectionId);
        Subscription subscription = connection.findSubscription(subscriptionId);

        // no work to do if subscription cannot be found
        if (subscription == null) {
            return;
        }

        // adjust denormalized cache here

        Set<String> resources = subscription.getResources();

        if (!resources.isEmpty()) {
            Map<String, DenormalizedCacheValue> denormalizedCacheValuesByResourceId =
                    syncCommands.mget(resources.toArray(String[]::new))
                            .stream()
                            .collect(Collectors.toMap(
                                    kv -> kv.getKey(),
                                    kv -> DenormalizedCacheValue.fromJson(kv.getValue())));

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
                                    es -> es.getValue().toJson()));

            // remove denormalized connections from a device if the list of subscriptions is empty

            this.logger.trace("modifiedResources: {} ", resourcesToModify);

            // delete resource cache
            if (resourcesToDelete.size() > 0) {
                syncCommands.del(resourcesToDelete.toArray(String[]::new));
            }

            // modify resource cache entries
            if (resourcesToModify.size() > 0) {
                syncCommands.mset(resourcesToModify);
            }
        }

        connection.removeSubscription(subscriptionId);
        syncCommands.set(connectionKey, connection.toJson());
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
                                        DenormalizedCacheValue.fromJson(String.format("{\"key\":\"%s\",\"connections\":[]}",
                                                kv.getKey())) :
                                        DenormalizedCacheValue.fromJson(kv.getValue()))
                        );

        assert resourceIds.size() == connectionsByResourceId.size();

        return connectionsByResourceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> dumpCache() {
        Set<String> keys = new HashSet<>(this.redisClient.sync().keys("*"));

        Map<String, Object> cache = new HashMap<>();
        for (String k : keys) {
            String type = this.redisClient.sync().type(k);
            if (type.equals("string")) cache.put(k, this.redisClient.sync().get(k));
            else if (type.equals("set")) cache.put(k, this.redisClient.sync().smembers(k));
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
                this.getDenormalizedConnectionsForResourceIds(
                        this.dumpCache()
                                .keySet()
                                .stream()
                                .filter(s -> !s.startsWith(CONNECTION_NAMESPACE) && !s.startsWith(CONNECTIONS_KEY))
                                .collect(Collectors.toSet()));

        for (var entry : denormalizedCacheValuesByKey.entrySet()) {
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
                                es -> es.getValue().toJson()));

        // remove denormalized connections from a device if the list of subscriptions is empty
        this.logger.debug("modifiedResources: {} ", resourcesToModify);

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
