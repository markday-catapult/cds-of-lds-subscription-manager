package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.AuthContext;
import com.catapult.lds.service.InvalidRequestException;
import com.catapult.lds.service.ResourceNameSpace;
import com.catapult.lds.service.Subscription;
import com.catapult.lds.service.SubscriptionAuthorizationService;
import com.catapult.lds.service.SubscriptionAuthorizationServiceImpl;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;
import com.catapult.lds.service.UnauthorizedUserException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * {@code SubscribeRequestHandler} is an implementation of {@link RequestHandler} that processes subscribe requests.
 */
public class SubscribeRequestHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The pattern of a namespaced resource, with the parts
     * <ul>
     *     <li>data class</li>
     *     <li>resource type</li>
     *     <li>user id</li>
     *     <li>resource key if defined</li>
     * </ul>
     */
    public static final String NAMESPACED_RESOURCE_PATTERN = "%s:%s:%s:%s";

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static final SubscriptionCacheService subscriptionCacheService = Util.cacheService;

    /**
     * Service used to validate the claims available in the request context
     *
     * @invariant claimsValidationService != null
     */
    private static final SubscriptionAuthorizationService subscriptionAuthorizationService = new SubscriptionAuthorizationServiceImpl();

    /**
     * The object mapper used by this handler.
     *
     * @invariant objectMapper != null;
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(SubscribeRequestHandler.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        if (event == null || event.getRequestContext() == null) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
            response.setBody("request context was not defined");
            return response;
        }

        final APIGatewayV2WebSocketEvent.RequestContext requestContext = event.getRequestContext();
        final String connectionId = requestContext.getConnectionId();
        final String eventBody = event.getBody();

        if (connectionId == null) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
            response.setBody("connectionId was not defined");
            return response;
        }

        if (eventBody == null) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
            response.setBody("event body was not defined");
            return response;
        }

        this.logger.info("{} invoked for connection: '{}'", this.getClass().getSimpleName(), connectionId);

        SubscriptionRequestContext subscriptionRequestContext =
                new SubscriptionRequestContext();

        try {
            String subscriptionID = this.processSubscriptionRequest(eventBody,
                    connectionId,
                    requestContext,
                    subscriptionRequestContext);

            return Util.createSubscriptionResponse(
                    HttpURLConnection.HTTP_CREATED,
                    subscriptionRequestContext.getRequestId(),
                    subscriptionID);

        } catch (JsonProcessingException e) {
            return Util.createErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                    subscriptionRequestContext.getRequestId(),
                    e.getMessage());

        } catch (InvalidRequestException e) {

            JSONArray validationViolations = new JSONArray();
            subscriptionRequestContext.getValidationViolations().forEach(v -> validationViolations.put(v));

            return Util.createErrorResponse(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    subscriptionRequestContext.getRequestId(),
                    validationViolations.toString());

        } catch (SubscriptionException e) {
            return Util.createErrorResponse(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    subscriptionRequestContext.getRequestId(),
                    e.getMessage());

        } catch (UnauthorizedUserException e) {
            return Util.createErrorResponse(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    subscriptionRequestContext.getRequestId(),
                    e.getMessage());
        }
    }

    /**
     * Returns the subscription id of the newly created subscription.
     *
     * @throws JsonProcessingException   if the given body cannot be deserialized into a
     *                                   {@linkplain SubscriptionRequest subscription request}.
     * @throws InvalidRequestException   if the subscription request is invalid.
     * @throws UnauthorizedUserException if the subscription request cannot be executed due to an authorization issue.
     * @throws SubscriptionException     if the subscription cannot be created
     * @pre body != null
     * @pre connectionId != null
     * @pre requestContext != null
     * @pre subscriptionRequestContext != null
     * @post
     */
    String processSubscriptionRequest(String body, String connectionId,
                                      APIGatewayV2WebSocketEvent.RequestContext requestContext,
                                      SubscriptionRequestContext subscriptionRequestContext)
            throws JsonProcessingException, SubscriptionException, UnauthorizedUserException, InvalidRequestException {
        assert body != null;
        assert connectionId != null;
        assert requestContext != null;
        assert subscriptionRequestContext != null;

        // Deserialize the request
        final SubscriptionRequest subscriptionRequest = objectMapper.readValue(body, SubscriptionRequest.class);
        subscriptionRequestContext.setRequestId(subscriptionRequest.getRequestId());

        // Validate the request
        if (subscriptionRequest.getRequestId() == null) {
            subscriptionRequestContext.validationViolations.add("Missing request id");
        }
        if (subscriptionRequest.getDataClass() == null) {
            subscriptionRequestContext.validationViolations.add("Missing data class");
        } else if (!Set.of("ts", "ad").contains(subscriptionRequest.getDataClass())) {
            subscriptionRequestContext.validationViolations.add("Invalid data class");
        }
        if (subscriptionRequest.getUserId() == null) {
            subscriptionRequestContext.validationViolations.add("Missing user id");
        }
        if (subscriptionRequest.getSampleRate() != null && !"ts".equals(subscriptionRequest.getDataClass())) {
            subscriptionRequestContext.validationViolations.add("Sample rate may only be defined for timeseries data");
        }
        if (subscriptionRequest.getSampleRate() != null && !Set.of(1, 2, 5, 10).contains(subscriptionRequest.getSampleRate())) {
            subscriptionRequestContext.validationViolations.add("If sample rate is defined, it may only have the " +
                    "value 1, 2, 5, or 10.");
        }
        if (subscriptionRequest.getNamespacedResources().isEmpty()) {
            subscriptionRequestContext.validationViolations.add("Could not generate resource keys");
        }
        if (!subscriptionRequestContext.validationViolations.isEmpty()) {
            throw new InvalidRequestException("Bad Request");
        }

        // authorize the request
        subscriptionAuthorizationService.checkAuthorizationForUserResource(subscriptionRequest.getUserId(),
                AuthContext.extractContext(requestContext.getAuthorizer()));

        // process the request
        Subscription subscription = Subscription.builder()
                .connectionId(connectionId)
                .resources(subscriptionRequest.getNamespacedResources())
                .sampleRate(subscriptionRequest.sampleRate)
                .build();

        // Add the subscription
        subscriptionCacheService.addSubscription(subscription);

        // return a successful response
        return subscription.getId();
    }

    /**
     * {@code SubscriptionRequest} contains information needed to create a new subscription.
     */
    @Value
    @Jacksonized
    @Builder
    public static class SubscriptionRequest {

        String action;
        String dataClass;
        String requestId;
        String userId;
        Integer sampleRate;
        SubscriptionRequestResources resources;

        /**
         * Returns a set of namespaced resources for this subscription request.
         *
         * @invariant return != null
         */
        public Set<String> getNamespacedResources() {
            Set<String> nameSpacedResources = new HashSet<>();

            if (this.resources == null) {
                return Collections.singleton(
                        String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                                this.dataClass,
                                ResourceNameSpace.USER.value(),
                                this.userId,
                                ""));
            }

            if (Objects.nonNull(this.resources.deviceIds)) {
                nameSpacedResources.addAll(this.resources.deviceIds.stream().map(d -> String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                        this.dataClass,
                        ResourceNameSpace.DEVICE.value(),
                        this.userId,
                        d)).collect(toSet()));
            }
            return nameSpacedResources;
        }

        /**
         * {@code SubscriptionRequestResources} contains information about the resources requested in a
         * {@link SubscribeRequestHandler.SubscriptionRequest}
         **/
        @Value
        @Jacksonized
        @Builder
        public static class SubscriptionRequestResources {
            @JsonProperty("deviceId")
            Set<String> deviceIds;
        }

    }

    /**
     * {@code SubscriptionRequestContext} contains information about a specific subscription request execution.
     */
    @Data
    @ToString
    static class SubscriptionRequestContext {
        private String requestId;
        private List<String> validationViolations = new ArrayList<>();
    }
}
