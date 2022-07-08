package com.catapult.lds.service;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RedisSubscriptionCacheServiceTest {

    /**
     * Clean up database before test
     */
    @BeforeTest
    void beforeTest() {
        String host = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_NAME);
        String port = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_PORT);
        RedisURI redisURI = RedisURI.create(host, Integer.valueOf(port));
        StatefulRedisClusterConnection<String, String> redisClient = RedisClusterClient.create(redisURI).connect();
        redisClient.sync().flushall();
    }

    @AfterTest
    void afterTest() {
        String host = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_NAME);
        String port = System.getenv(RedisSubscriptionCacheService.CLUSTER_ENV_PORT);
        RedisURI redisURI = RedisURI.create(host, Integer.valueOf(port));
        StatefulRedisClusterConnection<String, String> redisClient = RedisClusterClient.create(redisURI).connect();
        List<String> keys = redisClient.sync().keys("*");
        System.out.println("==========================");
        keys.forEach(k -> System.out.println(k + ": " + (redisClient.sync().type(k).equals("string") ?
                redisClient.sync().get(k) : redisClient.sync().hgetall(k))));
    }

    @Test
    void testRedisCanConnect() {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;
        Assert.assertTrue(cacheService.isConnected());
    }

    @Test
    void createAndCloseOneConnections() throws SubscriptionException {
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
    void createAndGetAndCancelSubscriptionsForOneConnection() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String connectionId1 = UUID.randomUUID().toString();

        String athleteResource1 = "athlete-id-1";
        String athleteResource2 = "athlete-id-2";
        String deviceResource1 = "device-id-1";
        String deviceResource2 = "device-id-2";
        List<String> allFourResourceIds = Arrays.asList(athleteResource1, athleteResource2, deviceResource1, deviceResource2);

        // asking for subscriptions for a non-existent connection should throw an exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.getSubscriptions(connectionId1));

        // create a connection
        cacheService.createConnection(connectionId1);

        // no subscriptions exist for this connection yet
        Assert.assertEquals(cacheService.getSubscriptions(connectionId1).size(), 0);

        Subscription subscription1 = new Subscription(connectionId1, Arrays.asList(athleteResource1, athleteResource2));
        Subscription subscription2 = new Subscription(connectionId1, Arrays.asList(athleteResource1, deviceResource1,
                deviceResource2));
        Subscription subscription3 = new Subscription(connectionId1, Arrays.asList(deviceResource1, athleteResource1));

        cacheService.putSubscription(subscription1);
        cacheService.putSubscription(subscription2);
        cacheService.putSubscription(subscription3);

        // 3 subscriptions exist for this connection
        Assert.assertEquals(cacheService.getSubscriptions(connectionId1).size(), 3);

        // all 4 resources have connections associated with them
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(allFourResourceIds).size(), 4);

        // cancel subscription 2
        cacheService.cancelSubscription(subscription2.getConnectionId(), subscription2.getId());

        // 2 subscriptions exist for this connection
        Assert.assertEquals(cacheService.getSubscriptions(connectionId1).size(), 2);

        // 'deviceResource2' no longer has a connection associates with it resources have connections associated with
        // them
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(allFourResourceIds).size(), 3);
        Assert.assertFalse(cacheService.getConnectionIdsForResourceIds(allFourResourceIds).containsKey(deviceResource2));
    }

    @Test
    void multipleConnectionsSharedResources() throws SubscriptionException {
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

        Subscription trainer1Subscription = new Subscription(trainer1ConnectionId, Arrays.asList(ath1Id, ath2Id, ath3Id));
        Subscription trainer2Subscription = new Subscription(trainer2ConnectionId, Arrays.asList(ath2Id, ath3Id, ath4Id));
        Subscription trainer3Subscription = new Subscription(trainer3ConnectionId, Arrays.asList(ath3Id, ath4Id, ath5Id));

        cacheService.putSubscription(trainer1Subscription);
        cacheService.putSubscription(trainer2Subscription);
        cacheService.putSubscription(trainer3Subscription);

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath1Id)).get(ath1Id).size(),
                1);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath2Id)).get(ath2Id).size(), 2);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath3Id)).get(ath3Id).size(), 3);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath4Id)).get(ath4Id).size(), 2);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath5Id)).get(ath5Id).size(), 1);

        // cancel subscription 1
        cacheService.cancelSubscription(trainer1Subscription.getConnectionId(), trainer1Subscription.getId());

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath1Id)).get(ath1Id).size(), 0);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath2Id)).get(ath2Id).size(), 1);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath3Id)).get(ath3Id).size(), 2);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath4Id)).get(ath4Id).size(), 2);
        Assert.assertEquals(cacheService.getConnectionIdsForResourceIds(Collections.singletonList(ath5Id)).get(ath5Id).size(), 1);

    }

}
