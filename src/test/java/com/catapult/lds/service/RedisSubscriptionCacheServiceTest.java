package com.catapult.lds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class RedisSubscriptionCacheServiceTest {

    /**
     * The logger used by this test.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(RedisSubscriptionCacheServiceTest.class);

    /**
     * Clean up database before test
     */
    @BeforeMethod
    void beforeTest() {
        this.logger.info("Cleaning cache");
        String host = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV)).orElse(
                "127.0.0.1");
        String port = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV)).orElse(
                "6379");
        this.logger.info("Connecting to {}:{}", host, port);
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));
        StatefulRedisConnection<String, String> redisClient = RedisClient.create(redisURI).connect();
        redisClient.sync().flushall();
    }

    @AfterMethod
    void afterTest() {
        String host = System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV);
        String port = System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV);
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));
        StatefulRedisConnection<String, String> redisClient = RedisClient.create(redisURI).connect();
        List<String> keys = redisClient.sync().keys("*");
        this.logger.info("==========================");
        keys.forEach(k -> this.logger.info(k + ": " + (redisClient.sync().type(k).equals("string") ?
                redisClient.sync().get(k) : redisClient.sync().hgetall(k))));
    }

    @Test
    void testRedisCanConnect() {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;
        Assert.assertTrue(cacheService.isConnected());
    }

    @Test
    void testCreateAndCloseOneConnections() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String connectionId1 = UUID.randomUUID().toString();

        // connection id does not exist
        Assert.assertFalse(cacheService.connectionExists(connectionId1));
        cacheService.createConnection(connectionId1);

        // connection id exists in the cache
        Assert.assertTrue(cacheService.connectionExists(connectionId1));

        // trying to create another connection with the same id throws an exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.createConnection(connectionId1));

        cacheService.closeConnection(connectionId1);
        cacheService.createConnection(connectionId1);

        // connection was closed and then recreated.  Trying to create another connection with the same id throws an
        // exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.createConnection(connectionId1));
        cacheService.closeConnection(connectionId1);

        String connectionId2 = UUID.randomUUID().toString();
        String connectionId3 = UUID.randomUUID().toString();
        cacheService.createConnection(connectionId2);
        cacheService.createConnection(connectionId3);
    }

    @Test
    void testCreateAndGetAndCancelSubscriptionsForOneConnection() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String connectionId1 = "CON1";

        String athleteResource1 = "athlete-id-1";
        String athleteResource2 = "athlete-id-2";
        String deviceResource1 = "device-id-1";
        String deviceResource2 = "device-id-2";
        Set<String> allFourResourceIds = Sets.newHashSet(athleteResource1, athleteResource2, deviceResource1,
                deviceResource2);

        // asking for subscriptions for a non-existent connection should throw an exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.getSubscriptions(connectionId1));

        // create a connection
        cacheService.createConnection(connectionId1);

        // no subscriptions exist for this connection yet
        Assert.assertEquals(cacheService.getSubscriptions(connectionId1).size(), 0);

        Subscription subscription1 = new Subscription(connectionId1, Sets.newHashSet(athleteResource1, athleteResource2));
        Subscription subscription2 = new Subscription(connectionId1, Sets.newHashSet(athleteResource1, deviceResource1,
                deviceResource2));
        Subscription subscription3 = new Subscription(connectionId1, Sets.newHashSet(deviceResource1, athleteResource1));

        cacheService.addSubscription(subscription1);
        cacheService.addSubscription(subscription2);
        cacheService.addSubscription(subscription3);

        // 3 subscriptions exist for this connection
        Assert.assertEquals(cacheService.getSubscriptions(connectionId1).size(), 3);

        // all 4 resources have the correct connections associated with them
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource1).getSubscriptionIds(connectionId1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource2).getSubscriptionIds(connectionId1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource1).getSubscriptionIds(connectionId1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource2).getSubscriptionIds(connectionId1).isEmpty());

        // cancel subscription 2
        cacheService.cancelSubscription(subscription2.getConnectionId(), subscription2.getId());

        // 2 subscriptions exist for this connection
        Assert.assertEquals(cacheService.getSubscriptions(connectionId1).size(), 2);

        // 'deviceResource2' no longer has a connection associates with it resources have connections associated with
        // them
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource1).getSubscriptionIds(connectionId1).isEmpty());
        Assert.assertTrue(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource2).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource2).getSubscriptionIds(connectionId1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource2).getSubscriptionIds(connectionId1).isEmpty());
    }

    @Test
    void testMultipleConnectionsSharedResources() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String trainer1ConnectionId = "trainer-1-connection-id";
        String trainer2ConnectionId = "trainer-2-connection-id";
        String trainer3ConnectionId = "trainer-3-connection-id";

        String ath1Id = "athlete-1-id";
        String ath2Id = "athlete-2-id";
        String ath3Id = "athlete-3-id";
        String ath4Id = "athlete-4-id";
        String ath5Id = "athlete-5-id";

        cacheService.createConnection(trainer1ConnectionId);
        cacheService.createConnection(trainer2ConnectionId);
        cacheService.createConnection(trainer3ConnectionId);

        Subscription trainer1Subscription = new Subscription(trainer1ConnectionId, Sets.newHashSet(ath1Id, ath2Id, ath3Id));
        Subscription trainer2Subscription = new Subscription(trainer2ConnectionId, Sets.newHashSet(ath2Id, ath3Id, ath4Id));
        Subscription trainer3Subscription = new Subscription(trainer3ConnectionId, Sets.newHashSet(ath3Id, ath4Id, ath5Id));

        cacheService.addSubscription(trainer1Subscription);
        cacheService.addSubscription(trainer2Subscription);
        cacheService.addSubscription(trainer3Subscription);

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath1Id)).get(ath1Id).getConnectionIds().size(),
                1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath2Id)).get(ath2Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath3Id)).get(ath3Id).getConnectionIds().size(), 3);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath4Id)).get(ath4Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath5Id)).get(ath5Id).getConnectionIds().size(), 1);

        // cancel subscription 1
        cacheService.cancelSubscription(trainer1Subscription.getConnectionId(), trainer1Subscription.getId());

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath1Id)).get(ath1Id).getConnectionIds().size(), 0);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath3Id)).get(ath3Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath4Id)).get(ath4Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath5Id)).get(ath5Id).getConnectionIds().size(), 1);

        // close a connection
        cacheService.closeConnection(trainer3ConnectionId);

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath1Id)).get(ath1Id).getConnectionIds().size(), 0);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath3Id)).get(ath3Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath4Id)).get(ath4Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath5Id)).get(ath5Id).getConnectionIds().size(), 0);

    }

    @Test
    void testIdenticalSubscriptions() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String trainer1ConnectionId = "trainer-1-connection-id";
        String ath1Id = "athlete-1-id";
        String ath2Id = "athlete-2-id";

        Subscription sub1 = new Subscription(trainer1ConnectionId, Sets.newHashSet(ath1Id, ath2Id));
        Subscription sub2 = new Subscription(trainer1ConnectionId, Sets.newHashSet(ath1Id, ath2Id));
        Subscription sub3 = new Subscription(trainer1ConnectionId, Sets.newHashSet(ath1Id, ath2Id));

        cacheService.createConnection(trainer1ConnectionId);
        cacheService.addSubscription(sub1);
        cacheService.addSubscription(sub2);
        cacheService.addSubscription(sub3);

        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath1Id)).get(ath1Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);

        cacheService.cancelSubscription(sub1.getConnectionId(), sub1.getId());

        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath1Id)).get(ath1Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);

        cacheService.closeConnection(sub2.getConnectionId());

        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath1Id)).get(ath1Id).getConnectionIds().size(), 0);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Collections.singleton(ath2Id)).get(ath2Id).getConnectionIds().size(), 0);
    }

    @Test
    public void testSerializeDeserializeDenormalizedCache() throws JsonProcessingException {

        String serialized = "[" +
                "{\"connectionId\":\"id-1231242\", \"subscriptionIds\":[\"sub-24234\",\"sub-93234\"]}," +
                "{\"connectionId\":\"id-utd12yw\", \"subscriptionIds\":[\"sub-w332r\",\"sub-daasde\"]}" +
                "]";

        DenormalizedCacheValue denormalizedCacheValue =
                DenormalizedCacheValue.deserializeFromJson("key", serialized);

        denormalizedCacheValue.addSubscription("con1", "sub1");
        denormalizedCacheValue.addSubscription("con1", "sub2");
        denormalizedCacheValue.addSubscription("con2", "sub2");

        String json = denormalizedCacheValue.getSerializedConnectionList();
        System.out.println(json);
    }
}
