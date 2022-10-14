import com.catapult.lds.SubscribeRequestHandler;
import com.catapult.lds.service.ResourceNameSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;

public class SubscribeRequestHandlerTest {
    /**
     * The logger used by this test.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(SubscribeRequestHandlerTest.class);

    @Test
    void testTimeseriesSubscriptionRequestDeviceNamespaces() {

        final String dataClass = "ts";

        final String userId = "user-id-guid";

        final String devId1 = "dev-id-15556567";
        final String devId2 = "dev-id-2342342";
        SubscribeRequestHandler.SubscriptionRequest.SubscriptionRequestResources resources =
                SubscribeRequestHandler.SubscriptionRequest.SubscriptionRequestResources.builder()
                        .deviceIds(Set.of(devId1, devId2)).build();

        SubscribeRequestHandler.SubscriptionRequest subscriptionRequest =
                SubscribeRequestHandler.SubscriptionRequest.builder()
                        .requestId("request-abc")
                        .action("subscribe")
                        .userId(userId)
                        .dataClass(dataClass)
                        .resources(resources)
                        .build();

        Set<String> namespacedResources = subscriptionRequest.getNamespacedResources();

        Assert.assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                ResourceNameSpace.DEVICE.value(),
                userId,
                devId1)));

        Assert.assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                ResourceNameSpace.DEVICE.value(),
                userId,
                devId1)));

        namespacedResources.forEach(r -> logger.info(r));
    }

    @Test
    void testTimeseriesSubscriptionRequestUserNamespaces() {

        final String dataClass = "ts";

        final String userId = "user-id-guid";

        final String devId1 = "dev-id-15556567";
        final String devId2 = "dev-id-2342342";
        SubscribeRequestHandler.SubscriptionRequest.SubscriptionRequestResources resources =
                SubscribeRequestHandler.SubscriptionRequest.SubscriptionRequestResources.builder()
                        .deviceIds(Set.of(devId1, devId2)).build();

        SubscribeRequestHandler.SubscriptionRequest subscriptionRequest =
                SubscribeRequestHandler.SubscriptionRequest.builder()
                        .requestId("request-abc")
                        .action("subscribe")
                        .userId(userId)
                        .dataClass(dataClass)
                        .build();

        Set<String> namespacedResources = subscriptionRequest.getNamespacedResources();

        Assert.assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                ResourceNameSpace.USER.value(),
                userId,
                "")));

        namespacedResources.forEach(r -> logger.info(r));
    }
}
