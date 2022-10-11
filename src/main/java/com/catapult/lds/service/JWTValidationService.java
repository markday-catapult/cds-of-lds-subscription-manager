package com.catapult.lds.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
/**
 * {@code JWTValidationService} provides methods for validating Open field Micro auth JWT token.
 */
public class JWTValidationService implements AuthorizationService {


    private static final String CONTEXT_CATAPULT_SPORTS = "catapultsports";
    private static final String CONTEXT_AUTH ="auth";
    private static final String CONTEXT_SUBJECT ="sub";
    private static final String CONTEXT_CLAIMS ="claims";
    private static final String CONTEXT_SCOPES ="scopes";
    private static final String CONTEXT_LDS_SCOPE = "com.catapultsports.services.LDS";

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(JWTValidationService.class);

    /**
     * The object mapper used by this service.
     *
     * @invariant objectMapper != null
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     * 
     * @pre userId != null
     * @pre requestContext != null
     * @throws SubscriptionException
     */
    @Override
    public void validateJWTToken(String userId, Map<String, Object> requestContext) throws SubscriptionException {

        assert (userId !=null);
        assert (requestContext != null);

        if (requestContext.get(CONTEXT_CATAPULT_SPORTS) != null) {
            JsonNode authContext = objectMapper.convertValue(requestContext.get(CONTEXT_CATAPULT_SPORTS), JsonNode.class);
            JsonNode auth = authContext.get(CONTEXT_AUTH);

            //validating userId
            String subject = auth.get(CONTEXT_CLAIMS).get(CONTEXT_SUBJECT).textValue();
            if (!subject.equalsIgnoreCase(userId)) {
                throw new SubscriptionException("Unauthorized user");
            }
            JsonNode scopes = auth.get(CONTEXT_CLAIMS).get(CONTEXT_SCOPES);

            //validating LDS claim
            boolean validToken = false;
            for (JsonNode scope : scopes) {
                if (scope.textValue().equalsIgnoreCase(CONTEXT_LDS_SCOPE))
                    validToken = true;
            }
            if (!validToken) {
                throw new SubscriptionException("User does not have permission to access live data");
            }
        }
    }
}
