package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SimpleCacheService;
import com.catapult.lds.service.SubscriptionCacheService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;

/**
 * {@code UnsubscribeRequestHandler} is an implementation of {@link RequestHandler} that processes unsubscribe
 * requests.
 */
public class UnsubscribeRequestHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static SubscriptionCacheService subscriptionCacheService = SimpleCacheService.instance;

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
        final UnsubscribeRequest unsubscribeRequest;

        // Deserialize and validate the request
        try {
            connectionId = event.getRequestContext().getConnectionId();
            unsubscribeRequest = UnsubscribeRequestHandler.objectMapper.readValue(event.getBody(),
                    UnsubscribeRequest.class);

            if (connectionId == null) {
                return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                        unsubscribeRequest.requestId, "connectionId was not defined");
            }
            if (unsubscribeRequest.requestId == null || unsubscribeRequest.subscriptionId == null) {
                return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                        unsubscribeRequest.requestId, "Unsubscribe request missing required fields");
            }
        } catch (JsonProcessingException e) {
            return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, null, e.getMessage());
        }

        // process the request
        String subscriptionId = unsubscribeRequest.subscriptionId;
        subscriptionCacheService.cancelSubscription(connectionId, subscriptionId);

        // return a successful response
        APIGatewayV2WebSocketResponse response = Util.createUnsubscribeResponse(
                HttpURLConnection.HTTP_OK,
                unsubscribeRequest.requestId);

        return response;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class UnsubscribeRequest {
        private String action;
        private String requestId;
        private String subscriptionId;

        @Override
        public String toString() {
            return "UnsubscribeRequest{" +
                    "action='" + action + '\'' +
                    ", requestId='" + requestId + '\'' +
                    ", subscriptionId='" + subscriptionId + '\'' +
                    '}';
        }
    }

}
