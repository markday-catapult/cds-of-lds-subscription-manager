package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SimpleCacheService;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;

import java.net.HttpURLConnection;

/**
 * {@code ConnectHandler} is an implementation of {@link RequestHandler} that establishes a WebSocket connection.
 */
public class DisconnectHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static SubscriptionCacheService subscriptionCacheService = SimpleCacheService.instance;

    /**
     * {@inheritDoc}
     */
    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        String connectionId = event.getRequestContext().getConnectionId();
        if (connectionId == null) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
            response.setBody("connectionId was not defined");
            return response;
        }

        try {
            subscriptionCacheService.closeConnection(connectionId);
            context.getLogger().log("connection closed.  Connection id: " + connectionId);
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
            response.setBody("ok");
            return response;
        } catch (SubscriptionException e) {
            context.getLogger().log(e.getMessage());
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_GONE);
            response.setBody(e.getMessage());
            return response;
        }
    }
}
