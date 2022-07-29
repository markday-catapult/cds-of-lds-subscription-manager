package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.Subscription;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code SubscribeRequestHandler} is an implementation of {@link RequestHandler} that processes subscribe requests.
 */
public class SubscribeRequestHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The pattern of a namespaced resource, with the parts
     * <ul>
     *     <li>data class</li>
     *     <li>resource type</li>
     *     <li>resource key</li>
     * </ul>
     */
    public static final String NAMESPACED_RESOURCE_PATTERN = "%s:%s:%s";

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static final SubscriptionCacheService subscriptionCacheService = Util.cacheService;

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

        logger.debug("Received subscribe request from connection: '{}'", connectionId);

        // Deserialize and validate the request
        try {
            subscriptionRequest = SubscribeRequestHandler.objectMapper.readValue(event.getBody(),
                    SubscriptionRequest.class);

            if (subscriptionRequest.requestId == null ||
                    subscriptionRequest.dataClass == null ||
                    subscriptionRequest.resources == null ||
                    subscriptionRequest.resources.isEmpty()) {
                return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                        subscriptionRequest.requestId, "Subscription request missing required fields");
            }
        } catch (JsonProcessingException e) {
            return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, null, e.getMessage());
        }

        // process the request
        try {
            Set<String> resources = subscriptionRequest.resources.entrySet()
                    .stream()
                    .map(e -> e.getValue().stream().map(s -> String.format("%s:%s:%s",
                                    subscriptionRequest.dataClass,
                                    e.getKey(),
                                    s))
                            .collect(Collectors.toSet()))
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

            Subscription subscription = new Subscription(connectionId, resources);

            // TODO: Authorize the subscription

            // Add the subscription
            subscriptionCacheService.putSubscription(subscription);

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
        }
    }

    /**
     * {@code SubscriptionRequest} contains information needed to create a new subscription.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Builder
    @AllArgsConstructor
    public static class SubscriptionRequest {
        private String action;
        private String dataClass;
        private String requestId;
        private Map<String, Collection<String>> resources;

        /**
         * Creates a new {@code SubscriptionRequest}.  For use by object mapper only
         */
        public SubscriptionRequest() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SubscriptionRequest{" +
                    "action='" + action + '\'' +
                    ", requestId='" + requestId + '\'' +
                    ", resources=" + resources +
                    '}';
        }

        /**
         * Returns a set of namespaced resources for this subscription request.
         *
         * @invariant return != null
         */
        public Set<String> getNamespacedResources() {
            return this.resources.entrySet()
                    .stream()
                    .map(e -> e.getValue().stream().map(s -> String.format(NAMESPACED_RESOURCE_PATTERN,
                                    this.dataClass,
                                    e.getKey(),
                                    s))
                            .collect(Collectors.toSet()))
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
        }
    }
}
