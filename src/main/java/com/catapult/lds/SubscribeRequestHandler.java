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
     * {@inheritDoc}
     */
    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        final String connectionId;
        final SubscriptionRequest subscriptionRequest;

        // Deserialize and validate the request
        try {
            connectionId = event.getRequestContext().getConnectionId();
            context.getLogger().log("Creating subscritpion for connection " + connectionId);

            subscriptionRequest = SubscribeRequestHandler.objectMapper.readValue(event.getBody(),
                    SubscriptionRequest.class);

            if (connectionId == null) {
                return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                        subscriptionRequest.requestId, "connectionId was not defined");
            }
            if (subscriptionRequest.requestId == null || subscriptionRequest.resources == null || subscriptionRequest.resources.size() == 0) {
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
                    .map(e -> e.getValue().stream().map(s -> e.getKey() + "-" + s).collect(Collectors.toSet()))
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

            Subscription subscription = new Subscription(connectionId, resources);

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
    public static class SubscriptionRequest {
        private String action;
        private String requestId;
        private Map<String, Collection<String>> resources;

        /**
         * Creates a new {@code SubscriptionRequest}.  For use by object mapper only
         */
        SubscriptionRequest() {
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
    }
}
