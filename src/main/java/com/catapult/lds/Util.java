package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.RedisSubscriptionCacheService;
import com.catapult.lds.service.SubscriptionCacheService;
import org.json.JSONObject;

import java.util.Map;

/**
 * {@code Util} provides generic convenience methods to all handlers.
 */
public class Util {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    public static SubscriptionCacheService cacheService = RedisSubscriptionCacheService.instance;

    public static APIGatewayV2WebSocketResponse createSubscriptionResponse(int status,
                                                                           String requestId,
                                                                           String subscriptionId) {
        JSONObject responseBody = new JSONObject();
        responseBody.put("status", status);
        responseBody.put("requestId", requestId);
        responseBody.put("subscriptionId", subscriptionId);

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(status);
        response.setBody(responseBody.toString());
        response.setHeaders(Map.of("Sec-WebSocket-Protocol","websocket"));
        return response;
    }

    public static APIGatewayV2WebSocketResponse createUnsubscribeResponse(int status,
                                                                          String requestId) {
        JSONObject responseBody = new JSONObject();
        responseBody.put("status", status);
        responseBody.put("requestId", requestId);

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(status);
        response.setBody(responseBody.toString());
        return response;
    }

    public static APIGatewayV2WebSocketResponse createSubscriptionErrorResponse(int status,
                                                                                String requestId,
                                                                                String errorMessage) {
        JSONObject responseBody = new JSONObject();
        responseBody.put("status", status);
        responseBody.put("requestId", requestId);
        responseBody.put("errorMessage", errorMessage);

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(status);
        response.setBody(responseBody.toString());
        return response;
    }
}
