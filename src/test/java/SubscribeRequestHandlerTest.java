import com.catapult.lds.SubscribeRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SubscribeRequestHandlerTest {
    /**
     * The logger used by this test.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(SubscribeRequestHandlerTest.class);

    @Test
    void testSubscriptionRequestNamespaces() {

        Map<String, Collection<String>> resourceMap = new HashMap<>();

        final String dataClass = "ts";

        final String devKey = "deviceId";
        final String athKey = "athleteId";

        final String devId1 = "dev-id-15556567";
        final String devId2 = "dev-id-2342342";
        final String athId1 = "ath-id-33223434";

        resourceMap.put(devKey, Arrays.asList(devId1, devId2));
        resourceMap.put(athKey, Arrays.asList(athId1));

        SubscribeRequestHandler.SubscriptionRequest subscriptionRequest =
                SubscribeRequestHandler.SubscriptionRequest.builder()
                        .requestId("request-abc")
                        .action("subscribe")
                        .dataClass(dataClass)
                        .resources(resourceMap)
                        .build();

        Set<String> namespacedResources = subscriptionRequest.getNamespacedResources();

        Assert.assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                devKey,
                devId1)));

        Assert.assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                devKey,
                devId1)));

        Assert.assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                athKey,
                athId1)));

        namespacedResources.forEach(r -> logger.info(r));
    }
}
