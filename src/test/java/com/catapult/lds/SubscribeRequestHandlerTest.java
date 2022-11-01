package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.catapult.lds.service.InvalidRequestException;
import com.catapult.lds.service.ResourceNameSpace;
import com.catapult.lds.service.SubscriptionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                ResourceNameSpace.DEVICE.value(),
                userId,
                devId1)));

        assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                ResourceNameSpace.DEVICE.value(),
                userId,
                devId1)));

        namespacedResources.forEach(r -> this.logger.info(r));
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

        assertTrue(namespacedResources.contains(String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                dataClass,
                ResourceNameSpace.USER.value(),
                userId,
                "")));

        namespacedResources.forEach(r -> this.logger.info(r));
    }

    @Test
    void testInvalidSubscriptions() throws Exception {
        SubscribeRequestHandler handler = new SubscribeRequestHandler();

        String body = "";
        APIGatewayV2WebSocketEvent.RequestContext requestContext = new APIGatewayV2WebSocketEvent.RequestContext();
        String connectionId = "connectionId";
        SubscribeRequestHandler.SubscriptionRequestContext subContext =
                new SubscribeRequestHandler.SubscriptionRequestContext();

        // empty string, should not process
        assertThrows(JsonProcessingException.class, () -> handler.processSubscriptionRequest("",
                connectionId, requestContext, subContext));

        // request invalid - empty -> invalid request
        assertThrows(InvalidRequestException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().toString(),
                connectionId, requestContext, subContext));
        System.out.println(subContext);
        assertEquals(3, subContext.getValidationViolations().size());
        assertTrue(subContext.getValidationViolations().contains("Missing request id"));
        assertTrue(subContext.getValidationViolations().contains("Missing data class"));
        assertTrue(subContext.getValidationViolations().contains("Missing user id"));
        subContext.getValidationViolations().clear();

        // request invalid - missing user and request ids -> invalid request
        assertThrows(InvalidRequestException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().put("dataClass", "ts").toString(),
                connectionId, requestContext, subContext));
        System.out.println(subContext);
        assertEquals(2, subContext.getValidationViolations().size());
        assertTrue(subContext.getValidationViolations().contains("Missing request id"));
        assertFalse(subContext.getValidationViolations().contains("Missing data class"));
        assertTrue(subContext.getValidationViolations().contains("Missing user id"));
        subContext.getValidationViolations().clear();

        // request invalid - bad data class -> invalid request
        assertThrows(InvalidRequestException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().put("dataClass", "fo").toString(),
                connectionId, requestContext, subContext));
        System.out.println(subContext);
        assertEquals(3, subContext.getValidationViolations().size());
        assertTrue(subContext.getValidationViolations().contains("Missing request id"));
        assertTrue(subContext.getValidationViolations().contains("Invalid data class"));
        assertTrue(subContext.getValidationViolations().contains("Missing user id"));
        subContext.getValidationViolations().clear();

        // request invalid - sample rate defined for aggregate data -> invalid request
        assertThrows(InvalidRequestException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().put("requestId", "req").put("userId", "usr").put("dataClass", "ad")
                        .put("sampleRate", 5).toString(),
                connectionId, requestContext, subContext));
        System.out.println(subContext);
        assertEquals(1, subContext.getValidationViolations().size());
        assertTrue(subContext.getValidationViolations().contains("Sample rate may only be defined for timeseries data"));
        subContext.getValidationViolations().clear();

        // request invalid - sample rate if defined must be 1,2,5 or 10 -> invalid request
        assertThrows(InvalidRequestException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().put("requestId", "req").put("userId", "usr").put("dataClass", "ts")
                        .put("sampleRate", 3).toString(),
                connectionId, requestContext, subContext));
        System.out.println(subContext);
        assertEquals(1, subContext.getValidationViolations().size());
        assertTrue(subContext.getValidationViolations().contains("If sample rate is defined, it may only have the value 1, 2, 5, or 10."));
        subContext.getValidationViolations().clear();

        // request invalid - resources may not be definied for aggregate data
        assertThrows(InvalidRequestException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().put("requestId", "req").put("userId", "usr").put("dataClass", "ad").put("resources",
                        new JSONObject()).toString(),
                connectionId, requestContext, subContext));
        System.out.println(subContext);
        assertEquals(1, subContext.getValidationViolations().size());
        assertTrue(subContext.getValidationViolations().contains("Could not generate resource keys"));
        subContext.getValidationViolations().clear();

        // valid request - fail on authorization
        assertThrows(SubscriptionException.class, () -> handler.processSubscriptionRequest(
                new JSONObject().put("requestId", "req").put("userId", "usr").put("dataClass", "ts")
                        .put("sampleRate", 2).toString(),
                connectionId, requestContext, subContext));
    }
}
