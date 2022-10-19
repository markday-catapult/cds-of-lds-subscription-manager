package com.catapult.lds.task;

import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiAsync;
import com.amazonaws.services.apigatewaymanagementapi.model.GetConnectionRequest;
import com.amazonaws.services.apigatewaymanagementapi.model.GoneException;
import com.catapult.lds.service.RedisSubscriptionCacheService;
import com.catapult.lds.service.RedisSubscriptionCacheServiceTest;
import com.catapult.lds.service.Subscription;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubscriptionMaintenanceTaskTest {

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
    void beforeTest() throws SubscriptionException {
        this.logger.info("Cleaning cache");
        String host = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV)).orElse(
                "127.0.0.1");
        String port = Optional.ofNullable(System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV)).orElse(
                "6379");
        this.logger.info("Connecting to {}:{}", host, port);
        RedisURI redisURI = RedisURI.create(host, Integer.parseInt(port));
        StatefulRedisConnection<String, String> redisClient = RedisClient.create(redisURI).connect();
        redisClient.sync().flushall();
        redisClient.sync().set("ad:user:66b9655c-25a9-4ef5-98c2-87a368675309-001-002", "[{\"connectionId\":\"YEPoaduliYcCJVg=\",\"subscriptionIds\":[\"bfd0d981-dd30-41d0-9e83-31fc6207e857\"]},{\"connectionId\":\"YEPodcN5iYcCEqw=\",\"subscriptionIds\":[\"d94575a2-eb53-4e1b-be13-2e248ad85e90\"]},{\"connectionId\":\"YEPoscsyiYcCFfg=\",\"subscriptionIds\":[\"c6a85bf8-084d-447e-a8d2-32dc49603b82\"]}]");
        redisClient.sync().set("ad:user:66b9655c-25a9-4ef5-98c2-87a368675309-001-001", "[{\"connectionId" +
                "\":\"YEPpBd35CYcCHJg=\",\"subscriptionIds\":[\"55869dea-9b5f-47eb-a5ce-6258df713c25\"]},{\"connectionId\":\"YEPpHfJXiYcCJgQ=\",\"subscriptionIds\":[\"89382d09-6c35-47a7-b6aa-7cd38d5a19ff\"]},{\"connectionId\":\"YEPpMcMnCYcCGsQ=\",\"subscriptionIds\":[\"646d1a55-7edb-4f5c-8c1b-b62fa1ed379a\"]},{\"connectionId\":\"YEPpTeCPiYcCEFQ=\",\"subscriptionIds\":[\"2da988cc-7d07-4efe-8721-12ecf6a58e0c\"]}]");
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;
        cacheService.createConnection("con-id-1", "sub-id");
        cacheService.createConnection("con-id-2", "sub-id");
        cacheService.addSubscription(Subscription.builder()
                .connectionId("con-id-1")
                .resources(Set.of("athlete-id-1"))
                .build());
    }

    @AfterMethod
    void afterTest() {
        String host = System.getenv(RedisSubscriptionCacheService.LDS_REDIS_HOST_ENV);
        String port = System.getenv(RedisSubscriptionCacheService.LDS_REDIS_PORT_ENV);
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
    void doTask() throws Exception {
        SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

        AmazonApiGatewayManagementApiAsync api = mock(AmazonApiGatewayManagementApiAsync.class);
        when(api.getConnection(any(GetConnectionRequest.class))).thenThrow(GoneException.class);

        ConnectionMaintenanceTask task = new ConnectionMaintenanceTask(
                cacheService,
                api,
                true
        );

        var result = task.call();

        var resultJson = new ObjectMapper().writeValueAsString(result);

        this.logger.info(resultJson);

        Assert.assertTrue(cacheService.isConnected());
    }

}
