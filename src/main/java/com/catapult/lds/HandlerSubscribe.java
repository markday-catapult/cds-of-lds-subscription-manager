package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

/**
 * {@code HandlerSubscribe} is an implementation of {@link RequestHandler} that consumes a subscription request from Websocket API Gateway
 * and persist the information in AWS Elasticache Redis instance
 */
public class HandlerSubscribe   implements RequestHandler<Object, String> {
    @Override
    public String handleRequest (Object o, Context context) {

        return null;
    }
}
