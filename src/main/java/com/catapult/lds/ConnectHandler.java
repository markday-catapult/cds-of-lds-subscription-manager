package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
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
    private static final SubscriptionCacheService subscriptionCacheService = Util.cacheService;

    /**
     * {@inheritDoc}
     */
    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        try {
            String connectionId = event.getRequestContext().getConnectionId();

            if (connectionId == null) {
                APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
                response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
                response.setBody("connectionId was not defined");
                return response;
            }

            subscriptionCacheService.createConnection(connectionId);

            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_OK);
            response.setBody("ok");
            return response;
        } catch (SubscriptionException e) {
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_CONFLICT);
            response.setBody(e.getMessage());
            return response;
        }
    }
}
