package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.RedisSubscriptionCacheService;
import com.catapult.lds.service.SubscriptionCacheService;
import org.json.JSONObject;

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

    /**
     * Creates an error response to be returned to the client with the given status, error message, and request id if
     * present.
     *
     * @pre status > 0
     * @pre errorMessage != null
     */
    public static APIGatewayV2WebSocketResponse createErrorResponse(int status,
                                                                    String requestId,
                                                                    String errorMessage) {

        assert status > 0;
        assert errorMessage != null;

        JSONObject responseBody = new JSONObject();
        responseBody.put("status", status);
        responseBody.put("errorMessage", errorMessage);
        if (requestId != null) {
            responseBody.put("requestId", requestId);
        }

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(status);
        response.setBody(responseBody.toString());
        return response;
    }
}
