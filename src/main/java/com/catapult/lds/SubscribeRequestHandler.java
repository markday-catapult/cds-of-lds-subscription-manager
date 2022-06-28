package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.InMemorySubscriptionCacheService;
import com.catapult.lds.service.SubscriptionCacheService;

/**
 * {@code SubscribeRequestHandler} is an implementation of {@link RequestHandler} that processes subscribe requests.
 */
public class SubscribeRequestHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static SubscriptionCacheService subscriptionCacheService = InMemorySubscriptionCacheService.instance;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        // TODO build the subscription, including resource ID transform
        SubscriptionCacheService.Subscription subscription = new SubscriptionCacheService.Subscription();

        String connectionId = event.getRequestContext().getConnectionId();
        subscriptionCacheService.putSubscription(subscription);

        return Util.createResponse(200, "ok");
    }
}
