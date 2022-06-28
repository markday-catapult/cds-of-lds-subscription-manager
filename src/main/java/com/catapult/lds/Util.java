package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;

public class Util {
    public static APIGatewayV2WebSocketResponse createResponse(int status, String body) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        response.setBody("ok");
        return response;
    }
}
