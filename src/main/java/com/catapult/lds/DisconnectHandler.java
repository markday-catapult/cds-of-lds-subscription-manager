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

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        String connectionId = event.getRequestContext().getConnectionId();

        try {
            subscriptionCacheService.closeConnection(connectionId);
            context.getLogger().log("connection closed.  Connection id: " + connectionId);
            return Util.createResponse(HttpURLConnection.HTTP_NO_CONTENT, "ok");
        } catch (SubscriptionException e) {
            context.getLogger().log(e.getMessage());
            return Util.createResponse(HttpURLConnection.HTTP_GONE, e.getMessage());
        }
    }
}
