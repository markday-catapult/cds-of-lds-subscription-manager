package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SimpleCacheService;
import com.catapult.lds.service.SubscriptionCacheService;

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

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        // TODO: extract subscription id
        String subscriptionId = "";

        String connectionId = event.getRequestContext().getConnectionId();
        subscriptionCacheService.cancelSubscription(connectionId, subscriptionId);

        context.getLogger().log("subscription removed.  Connection id: " + connectionId + " subscriptionId: " + subscriptionId);

        return Util.createResponse(200, "unsubscribe ok");
    }
}
