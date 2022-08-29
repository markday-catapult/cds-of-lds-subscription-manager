package com.catapult.lds;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.catapult.lds.authorization.PolicyDocument;
import com.catapult.lds.authorization.Response;
import com.catapult.lds.authorization.Statement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code ConnectAuthorizationHandler} is an implementation of {@link RequestHandler} that handles authorization for the
 * {@linkplain ConnectHandler connect} handler.
 */
public class ConnectAuthorizationHandler implements RequestHandler<APIGatewayProxyRequestEvent, Response> {

    /**
     * The name of the header containing the authorization token
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * The policy action for allowing the connection to open
     */
    public static final String POLICY_ACTION_ALLOW = "Allow";

    /**
     * The policy action for denying the connection
     */
    public static final String POLICY_ACTION_DENY = "Deny";

    /**
     * The bearer token
     */
    public static final String BEARER_TOKEN = "Bearer ";

    /**
     * The name of the environment variable which has a value of the execution arn
     */
    public static final String EXECUTE_ARN_ENV = "EXECUTE_ARN";

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(ConnectAuthorizationHandler.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Response handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, String> responseContext = new HashMap<String, String>();

        if (event == null ||
                event.getRequestContext() == null ||
                event.getRequestContext().getIdentity() == null
        ) {
            responseContext.put("event", "invalid event");
            return getPolicy("<no account id>", POLICY_ACTION_DENY, responseContext);
        }

        APIGatewayProxyRequestEvent.ProxyRequestContext proxyContext = event.getRequestContext();
        APIGatewayProxyRequestEvent.RequestIdentity identity = proxyContext.getIdentity();
        String accountId = identity.getAccountId();

        Map<String, String> headers = event.getHeaders();

        logger.debug("Received Authorization request: {} ", event);
        
        String authorizationToken = headers.get(AUTHORIZATION_HEADER);

        logger.debug("Auth token: '{}'" + authorizationToken);

        if (authorizationToken == null || authorizationToken.isEmpty()) {
            return getPolicy(accountId, POLICY_ACTION_DENY, responseContext);
        }

        logger.debug("Bearer Token: {}", authorizationToken.contains(BEARER_TOKEN));

        if (!authorizationToken.contains(BEARER_TOKEN)) {
            return getPolicy(accountId, POLICY_ACTION_DENY, responseContext);
        }

        String token = authorizationToken.substring(BEARER_TOKEN.length());

        // TODO: Authorization here

        responseContext.put("authorization", authorizationToken);

        return getPolicy(accountId, POLICY_ACTION_ALLOW, responseContext);
    }

    private Response getPolicy(String accountId, String effect, Map<String, String> responseContext) {

        String arn = System.getenv(EXECUTE_ARN_ENV);

        Statement statement = Statement.builder().
                effect(effect)
                .resource(arn)
                .build();

        PolicyDocument policyDocument = PolicyDocument.builder()
                .statements(Collections.singletonList(statement))
                .build();

        ObjectMapper mapper = new ObjectMapper();

        try {
            logger.info("Policy document: " + mapper.writeValueAsString(policyDocument));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return Response.builder()
                .principalId(accountId)
                .policyDocument(policyDocument)
                .context(responseContext)
                .build();

    }
}
