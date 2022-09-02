package com.catapult.lds;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiAsync;
import com.amazonaws.services.apigatewaymanagementapi.AmazonApiGatewayManagementApiAsyncClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.catapult.lds.service.SubscriptionCacheService;
import com.catapult.lds.task.ConnectionMaintenanceTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;

/**
 * {@code ConnectionMaintenanceHandler} is an implementation of {@link RequestHandler} that handles cleanup and removal
 * of dead connections.
 */
public class ConnectionMaintenanceHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    /**
     * The name of the environment variable which has a value of the url api gateway.
     */
    public final static String WEBSOCKET_CONNECTION_URL_ENV = "LDS_WEBSOCKET";

    /**
     * The name of the environment variable which has a value of websocket connect timeout in milliseconds.
     */
    public final static String WEBSOCKET_CONNECTION_TIMEOUT_ENV = "LDS_WEBSOCKET_CONNECT_TIMEOUT_MILLISECONDS";

    /**
     * The name of the environment variable which has a value of aws region.
     */
    public final static String AWS_REGION_ENV = "AWS_REGION";

    /**
     * The singleton {@code SubscriptionCacheService}
     *
     * @invariant subscriptionCacheService != null
     */
    private static final SubscriptionCacheService subscriptionCacheService = Util.cacheService;

    /**
     * The object mapper used by all instances of this handler.
     *
     * @invariant objectMapper != null;
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The logger used by all instances of this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(ConnectionMaintenanceHandler.class);

    /**
     * Websocket connect timeout in seconds.
     *
     * @invariant connectTimeout != null
     */
    private final Integer connectionTimeout;

    /**
     * The asynchronous api client used by this websocket manager.
     *
     * @invariant client != null
     */
    private final AmazonApiGatewayManagementApiAsync client;

    {
        String signingRegion = System.getenv(AWS_REGION_ENV);
        String websocketConnectionUrl = System.getenv(WEBSOCKET_CONNECTION_URL_ENV);
        connectionTimeout = Integer.parseInt(System.getenv(WEBSOCKET_CONNECTION_TIMEOUT_ENV));

        AmazonApiGatewayManagementApiAsyncClientBuilder builder = AmazonApiGatewayManagementApiAsyncClientBuilder.standard();
        AwsClientBuilder.EndpointConfiguration config =
                new AwsClientBuilder.EndpointConfiguration(websocketConnectionUrl, signingRegion);
        builder.setEndpointConfiguration(config);
        client = builder.build();
    }

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

        boolean dumpCache = false;

        final String responseBody;
        try {
            ConnectionMaintenanceTask.ConnectionMaintenanceResult taskResult =
                    new ConnectionMaintenanceTask(subscriptionCacheService, client, dumpCache).call();

            responseBody = dumpCache ? objectMapper.writeValueAsString(taskResult) : "ok";

        } catch (Exception e) {
            logger.debug(e.getMessage());
            APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
            response.setStatusCode(HttpURLConnection.HTTP_GONE);
            response.setBody(e.getMessage());
            return response;
        }

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(HttpURLConnection.HTTP_OK);
        response.setBody(responseBody);
        return response;
    }
}
