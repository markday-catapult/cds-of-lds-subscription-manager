package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.RedisSubscriptionCacheService;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;

import java.net.HttpURLConnection;

/**
 * {@code ConnectHandler} is an implementation of {@link RequestHandler} that establishes a WebSocket connection.
 */
public class ConnectHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static SubscriptionCacheService subscriptionCacheService = RedisSubscriptionCacheService.instance;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        context.getLogger().log("Handling request: " + event.toString());
        String connectionId = event.getRequestContext().getConnectionId();

        try {
            context.getLogger().log("creating connection: " + connectionId);
            subscriptionCacheService.createConnection(connectionId);
            context.getLogger().log("connection opened.  Connection id: " + connectionId);
            return Util.createResponse(HttpURLConnection.HTTP_OK, "ok");
        } catch (SubscriptionException e) {
            context.getLogger().log(e.getMessage());
            return Util.createResponse(HttpURLConnection.HTTP_CONFLICT, e.getMessage());
        }

    }
}
