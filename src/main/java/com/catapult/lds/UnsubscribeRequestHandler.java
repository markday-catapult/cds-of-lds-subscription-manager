package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final Logger logger = LoggerFactory.getLogger(UnsubscribeRequestHandler.class);

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

        String connectionId = event.getRequestContext().getConnectionId();
        if (connectionId == null) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
            response.setBody("connectionId was not defined");
            return response;
        }

        this.logger.info("{} invoked for connection: '{}'", this.getClass().getSimpleName(), connectionId);

        final UnsubscribeRequest unsubscribeRequest;

        // Deserialize and validate the request
        try {
            unsubscribeRequest = UnsubscribeRequestHandler.objectMapper.readValue(event.getBody(),
                    UnsubscribeRequest.class);

        } catch (JsonProcessingException e) {
            return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST, null, e.getMessage());
        }

        if (unsubscribeRequest.requestId == null || unsubscribeRequest.subscriptionId == null) {
            return Util.createSubscriptionErrorResponse(HttpURLConnection.HTTP_BAD_REQUEST,
                    unsubscribeRequest.requestId, "Unsubscribe request missing required fields ");
        }

        // process the request
        String subscriptionId = unsubscribeRequest.subscriptionId;
        logger.debug("Cancelling subscription: '{}'", subscriptionId);
        try {
            subscriptionCacheService.cancelSubscription(connectionId, subscriptionId);

            // return a successful response
            return Util.createUnsubscribeResponse(
                    HttpURLConnection.HTTP_OK,
                    unsubscribeRequest.requestId);
        } catch (SubscriptionException e) {
            return Util.createSubscriptionErrorResponse(
                    HttpURLConnection.HTTP_GONE,
                    unsubscribeRequest.requestId,
                    e.getMessage());
        }
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
