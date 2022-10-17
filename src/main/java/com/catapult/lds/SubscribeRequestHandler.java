package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
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
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashSet;
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

        final String connectionId;
        final SubscriptionRequest subscriptionRequest;

        connectionId = event.getRequestContext().getConnectionId();

        if (connectionId == null) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
            response.setBody("connectionId was not defined");
            return response;
        }

        this.logger.info("{} invoked for connection: '{}'", this.getClass().getSimpleName(), connectionId);

        // Deserialize and validate the request
        try {
            subscriptionRequest = SubscribeRequestHandler.objectMapper.readValue(event.getBody(),
                    SubscriptionRequest.class);

            if (subscriptionRequest.requestId == null ||
                    subscriptionRequest.dataClass == null ||
                    subscriptionRequest.userId == null ||
                    subscriptionRequest.resources == null) {
                return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                        subscriptionRequest.requestId, "Subscription request missing required fields");
            }
            if (subscriptionRequest.getNamespacedResources().isEmpty()) {
                return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                        subscriptionRequest.requestId, "Subscription request missing resources");

            }
        } catch (JsonProcessingException e) {
            return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, null, e.getMessage());
        }

        // process the request
        try {
            Set<String> resources = subscriptionRequest.getNamespacedResources();

            Subscription subscription = new Subscription(connectionId, resources);

            // JWT claims validation
            subscriptionAuthorizationService.validateClaims(subscriptionRequest.getUserId(), event.getRequestContext().getAuthorizer());

            // Add the subscription
            subscriptionCacheService.addSubscription(subscription);

            // return a successful response
            return Util.createSubscriptionResponse(
                    HttpURLConnection.HTTP_CREATED,
                    subscriptionRequest.requestId,
                    subscription.getId());
        } catch (SubscriptionException e) {
            // notify the client of any error
            return Util.createSubscriptionErrorResponse(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    subscriptionRequest.requestId,
                    e.getMessage());
        } catch (UnauthorizedUserException e) {
            // notify the client of any error
            return Util.createSubscriptionErrorResponse(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    subscriptionRequest.requestId,
                    e.getMessage());
        }
    }

    /**
     * {@code SubscriptionRequest} contains information needed to create a new subscription.
     */
    @Value
    @Jacksonized
    @Builder
    public static class SubscriptionRequest {

        private String action;
        private String dataClass;
        private String requestId;
        private String userId;
        private SubscriptionRequestResources resources;

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
                                dataClass,
                                ResourceNameSpace.USER.value(),
                                this.userId,
                                ""));
            }

            if (Objects.nonNull(this.resources.deviceIds)) {
                nameSpacedResources.addAll(this.resources.deviceIds.stream().map(d -> String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                        dataClass,
                        ResourceNameSpace.DEVICE.value(),
                        this.userId,
                        d)).collect(toSet()));
            }
            return nameSpacedResources;
        }

        /**
         * {@code SubscriptionRequestResources} contains information about the resources requested in a {@link SubscribeRequestHandler.SubscriptionRequest}
         **/
        @Value
        @Jacksonized
        @Builder
        public static class SubscriptionRequestResources {
            @JsonProperty("deviceId")
            private Set<String> deviceIds;
        }

    }
}
