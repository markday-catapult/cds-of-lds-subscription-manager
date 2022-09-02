package com.catapult.lds.task;

import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiAsync;
import com.catapult.lds.service.RedisSubscriptionCacheService;
import com.catapult.lds.service.RedisSubscriptionCacheServiceTest;
import com.catapult.lds.service.SubscriptionCacheService;
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

import static org.mockito.Mockito.mock;

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
