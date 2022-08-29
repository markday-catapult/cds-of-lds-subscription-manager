package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SubscriptionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;

/**
 * {@code ConnectionMaintenanceHandler} is an implementation of {@link RequestHandler} that handles cleanup and removal
 * of dead connections.
 */
public class ConnectionMaintenanceHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static final SubscriptionCacheService subscriptionCacheService = Util.cacheService;

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(ConnectionMaintenanceHandler.class);

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

        this.logger.info("request's connection id: {} ", connectionId);
        this.logger.info("open connections: {} ", subscriptionCacheService.getAllConnectionIds());

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(HttpURLConnection.HTTP_OK);
        response.setBody("ok");
        return response;
    }
}
