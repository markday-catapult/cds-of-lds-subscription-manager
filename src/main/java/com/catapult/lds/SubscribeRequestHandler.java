package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SimpleCacheService;
import com.catapult.lds.service.Subscription;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.service.SubscriptionException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

/**
 * {@code SubscribeRequestHandler} is an implementation of {@link RequestHandler} that processes subscribe requests.
 */
public class SubscribeRequestHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static SubscriptionCacheService subscriptionCacheService = SimpleCacheService.instance;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        // TODO build the subscription, including resource ID transform
        String requestBody = event.getBody();

        String connectionId = event.getRequestContext().getConnectionId();

        context.getLogger().log("body: " + requestBody);
        context.getLogger().log("connectionId: " + connectionId);

        try {
            Subscription subscription = new Subscription(connectionId);
            subscriptionCacheService.createConnection(connectionId);
            subscriptionCacheService.putSubscription(subscription);

            JSONObject responseBody = new JSONObject();
            responseBody.put("subscriptionId", subscription.getId());

            APIGatewayV2WebSocketResponse response = Util.createResponse(HttpURLConnection.HTTP_OK, responseBody.toString());

            context.getLogger().log("Responding with: " + response.toString());
            return response;
        } catch (SubscriptionException e) {
            return Util.createResponse(HttpURLConnection.HTTP_INTERNAL_ERROR, e.getMessage());
        }

    }
}
