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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

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
        String host = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV)).orElse(
                "127.0.0.1");
        String port = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV)).orElse(
                "6379");
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));
        StatefulRedisConnection<String, String> redisClient = RedisClient.create(redisURI).connect();
        List<String> keys = redisClient.sync().keys("*");
        this.logger.info("==========================");
        keys.forEach(k -> this.logger.info(k + ": " + (
                redisClient.sync().type(k).equals("string") ? redisClient.sync().get(k) :
                        redisClient.sync().type(k).equals("set") ? redisClient.sync().smembers(k) :
                                redisClient.sync().hgetall(k)))
        );
    }

    @Test
    void testRedisCanConnect() {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;
        Assert.assertTrue(cacheService.isConnected());
    }

    @Test
    void testCreateAndCloseOneConnections() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String id1 = UUID.randomUUID().toString();
        String subscriberId = "subscriber-id";

        // connection id does not exist
        Assert.assertFalse(cacheService.getAllConnectionIds().contains(id1));
        cacheService.createConnection(id1, subscriberId);

        // connection id exists in the cache
        Assert.assertTrue(cacheService.getAllConnectionIds().contains(id1));

        // trying to create another connection with the same id throws an exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.createConnection(id1, subscriberId));

        cacheService.closeConnection(id1);
        cacheService.createConnection(id1, subscriberId);

        // connection was closed and then recreated.  Trying to create another connection with the same id throws an
        // exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.createConnection(id1, subscriberId));
        cacheService.closeConnection(id1);

        String id2 = UUID.randomUUID().toString();
        String id3 = UUID.randomUUID().toString();
        cacheService.createConnection(id2, subscriberId);
        cacheService.createConnection(id3, subscriberId);
    }

    @Test
    void testCreateAndGetAndCancelSubscriptionsForOneConnection() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String connection1 = "CON1";

        String athleteResource1 = "athlete-id-1";
        String athleteResource2 = "athlete-id-2";
        String deviceResource1 = "device-id-1";
        String deviceResource2 = "device-id-2";
        Set<String> allFourResourceIds = Sets.newHashSet(athleteResource1, athleteResource2, deviceResource1,
                deviceResource2);

        // asking for subscriptions for a non-existent connection should throw an exception
        Assert.assertThrows(SubscriptionException.class, () -> cacheService.getConnection(connection1));

        // create a connection
        cacheService.createConnection(connection1, "subscriber-id");

        // no subscriptions exist for this connection yet
        Assert.assertEquals(cacheService.getConnection(connection1).getSubscriptions().size(), 0);

        Subscription subscription1 = Subscription.builder()
                .connectionId(connection1)
                .resources(Set.of(athleteResource1, athleteResource2))
                .build();
        Subscription subscription2 = Subscription.builder()
                .connectionId(connection1)
                .resources(Set.of(athleteResource1, deviceResource1, deviceResource2))
                .sampleRate(1)
                .build();
        Subscription subscription3 = Subscription.builder()
                .connectionId(connection1)
                .resources(Set.of(athleteResource1, deviceResource1))
                .sampleRate(2)
                .build();

        cacheService.addSubscription(subscription1);
        cacheService.addSubscription(subscription2);
        cacheService.addSubscription(subscription3);

        // 3 subscriptions exist for this connection
        Assert.assertEquals(cacheService.getConnection(connection1).getSubscriptions().size(), 3);

        // all 4 resources have the correct connections associated with them
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource1).getSubscriptionIds(connection1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource2).getSubscriptionIds(connection1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource1).getSubscriptionIds(connection1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource2).getSubscriptionIds(connection1).isEmpty());

        // cancel subscription 2
        cacheService.cancelSubscription(subscription2.getConnectionId(), subscription2.getId());

        // 2 subscriptions exist for this connection
        Assert.assertEquals(cacheService.getConnection(connection1).getSubscriptions().size(), 2);

        // 'deviceResource2' no longer has a connection associates with it resources have connections associated with
        // them
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource1).getSubscriptionIds(connection1).isEmpty());
        Assert.assertTrue(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(deviceResource2).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource2).getSubscriptionIds(connection1).isEmpty());
        Assert.assertFalse(cacheService.getDenormalizedConnectionsForResourceIds(allFourResourceIds).get(athleteResource2).getSubscriptionIds(connection1).isEmpty());
    }

    @Test
    void testMultipleConnectionsSharedResources() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String trainer1id = "trainer-1-connection-id";
        String trainer2id = "trainer-2-connection-id";
        String trainer3id = "trainer-3-connection-id";

        String ath1Id = "athlete-1-id";
        String ath2Id = "athlete-2-id";
        String ath3Id = "athlete-3-id";
        String ath4Id = "athlete-4-id";
        String ath5Id = "athlete-5-id";

        String subscriber1Id = "subscriber-1-id";

        cacheService.createConnection(trainer1id, subscriber1Id);
        cacheService.createConnection(trainer2id, subscriber1Id);
        cacheService.createConnection(trainer3id, subscriber1Id);

        Subscription trainer1Subscription = Subscription.builder()
                .connectionId(trainer1id)
                .resources(Set.of(ath1Id, ath2Id, ath3Id))
                .build();
        Subscription trainer2Subscription = Subscription.builder()
                .connectionId(trainer2id)
                .resources(Set.of(ath2Id, ath3Id, ath4Id))
                .sampleRate(5)
                .build();
        Subscription trainer3Subscription = Subscription.builder()
                .connectionId(trainer3id)
                .resources(Set.of(ath3Id, ath4Id, ath5Id))
                .sampleRate(10)
                .build();

        cacheService.addSubscription(trainer1Subscription);
        cacheService.addSubscription(trainer2Subscription);
        cacheService.addSubscription(trainer3Subscription);

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath1Id)).get(ath1Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath2Id)).get(ath2Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath3Id)).get(ath3Id).getConnectionIds().size(), 3);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath4Id)).get(ath4Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath5Id)).get(ath5Id).getConnectionIds().size(), 1);

        // cancel subscription 1
        cacheService.cancelSubscription(trainer1Subscription.getConnectionId(), trainer1Subscription.getId());

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath1Id)).get(ath1Id).getConnectionIds().size(), 0);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath3Id)).get(ath3Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath4Id)).get(ath4Id).getConnectionIds().size(), 2);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath5Id)).get(ath5Id).getConnectionIds().size(), 1);

        // close a connection
        cacheService.closeConnection(trainer3id);

        // make sure each resource has the appropriate number of connections
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath1Id)).get(ath1Id).getConnectionIds().size(), 0);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath3Id)).get(ath3Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath4Id)).get(ath4Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath5Id)).get(ath5Id).getConnectionIds().size(), 0);
    }

    @Test
    void testIdenticalSubscriptions() throws SubscriptionException {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        String trainer1id = "trainer-1-connection-id";
        String ath1Id = "athlete-1-id";
        String ath2Id = "athlete-2-id";

        String subscriberId = "subscriber-id";

        Subscription sub1 = Subscription.builder()
                .connectionId(trainer1id)
                .resources(Set.of(ath1Id, ath2Id))
                .sampleRate(null)
                .build();
        Subscription sub2 = Subscription.builder()
                .connectionId(trainer1id)
                .resources(Set.of(ath1Id, ath2Id))
                .sampleRate(null)
                .build();
        Subscription sub3 = Subscription.builder()
                .connectionId(trainer1id)
                .resources(Set.of(ath1Id, ath2Id))
                .sampleRate(3)
                .build();

        cacheService.createConnection(trainer1id, subscriberId);
        cacheService.addSubscription(sub1);
        cacheService.addSubscription(sub2);
        cacheService.addSubscription(sub3);

        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath1Id)).get(ath1Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);

        cacheService.cancelSubscription(sub1.getConnectionId(), sub1.getId());

        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath1Id)).get(ath1Id).getConnectionIds().size(), 1);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath2Id)).get(ath2Id).getConnectionIds().size(), 1);

        cacheService.closeConnection(sub2.getConnectionId());

        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath1Id)).get(ath1Id).getConnectionIds().size(), 0);
        Assert.assertEquals(cacheService.getDenormalizedConnectionsForResourceIds(Set.of(ath2Id)).get(ath2Id).getConnectionIds().size(), 0);
    }

    @Test
    public void testSerializeDeserializeDenormalizedCache() throws JsonProcessingException {

        String serialized = "{\"key\":\"key\",\"connections\":[" +
                "{\"id\":\"id-1231242\",\"subscriptions\":[{\"id\":\"sub-24234\"},{\"id\":\"sub-93234\",\"sampleRate\":1}]}," +
                "{\"id\":\"id-utd12yw\",\"subscriptions\":[{\"id\":\"sub-w332r\",\"sampleRate\":3},{\"id\":\"sub-dasde\"}]}" +
                "]}";

        DenormalizedCacheValue denormalizedCacheValue =
                DenormalizedCacheValue.fromJson(serialized);

        denormalizedCacheValue.addSubscription(Subscription.builder().connectionId("con1").id("sub1").resource("key").build());
        denormalizedCacheValue.addSubscription(Subscription.builder().connectionId("con1").id("sub2").resource("key").build());
        denormalizedCacheValue.addSubscription(Subscription.builder().connectionId("con2").id("sub2").resource("key").build());

        Map<String, Set<String>> subscriptionIdsByConnection =
                denormalizedCacheValue.getSubscriptionIdsByConnectionId();

        assertEquals(4, subscriptionIdsByConnection.keySet().size());
        assertEquals(2, subscriptionIdsByConnection.get("con1").size());

        this.logger.info("{}", denormalizedCacheValue);
        String json = denormalizedCacheValue.toJson();
        this.logger.info(json);
    }

    @Test
    public void testCleanup() {
        // add an orphaned subscription to denormalized cache
        this.logger.info("Adding orphans");
        String host = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV)).orElse(
                "127.0.0.1");
        String port = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV)).orElse(
                "6379");
        this.logger.info("Connecting to {}:{}", host, port);
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));
        StatefulRedisConnection<String, String> redisClient = RedisClient.create(redisURI).connect();
        redisClient.sync().set("ad:user:66b9655c-25a9-4ef5-98c2-87a368675309-001-002", "{\"key\":\"ad:user:66b9655c-25a9-4ef5-98c2-87a368675309-001-002\",\"connections\":[{\"id\":\"YEPoaduliYcCJVg=\",\"subscriptions\":[{\"id\":\"bfd0d981-dd30-41d0-9e83-31fc6207e857\"}]},{\"id\":\"YEPodcN5iYcCEqw=\",\"subscriptions\":[{\"id\":\"d94575a2-eb53-4e1b-be13-2e248ad85e90\",\"sampleRate\":10}]},{\"id\":\"YEPoscsyiYcCFfg=\",\"subscriptions\":[{\"id\":\"c6a85bf8-084d-447e-a8d2-32dc49603b82\"}]}]}");
        redisClient.sync().set("ad:user:66b9655c-25a9-4ef5-98c2-87a368675309-001-001", "{\"key\":\"ad:user:66b9655c-25a9-4ef5-98c2-87a368675309-001-001\",\"connections\":[{\"id\":\"YEPpBd35CYcCHJg=\",\"subscriptions\":[{\"id\":\"55869dea-9b5f-47eb-a5ce-6258df713c25\"}]},{\"id\":\"YEPpHfJXiYcCJgQ=\",\"subscriptions\":[{\"id\":\"89382d09-6c35-47a7-b6aa-7cd38d5a19ff\"}]},{\"id\":\"YEPpMcMnCYcCGsQ=\",\"subscriptions\":[{\"id\":\"646d1a55-7edb-4f5c-8c1b-b62fa1ed379a\"}]},{\"id\":\"YEPpTeCPiYcCEFQ=\",\"subscriptions\":[{\"id\":\"2da988cc-7d07-4efe-8721-12ecf6a58e0c\"}]}]}");

        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        Function<Set<String>, Set<String>> connectionFilter =
                x -> x.stream().filter(s -> s.contains("g") || s.contains("CEqw")).collect(Collectors.toSet());

        cacheService.cleanCache(connectionFilter);
        this.logger.info(cacheService.dumpCache().toString());
    }
}
