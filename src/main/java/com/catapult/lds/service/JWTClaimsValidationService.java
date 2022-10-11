package com.catapult.lds.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
/**
 * {@code JWTValidationService} provides methods for validating Open field Micro auth JWT claims set in Lambda request context.
 */
public class JWTClaimsValidationService implements ClaimsValidationService {


    private static final String CONTEXT_CATAPULT_SPORTS = "catapultsports";
    private static final String CONTEXT_LDS_SCOPE = "com.catapultsports.services.LDS";

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(JWTClaimsValidationService.class);

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
    public void validateClaims(String userId, Map<String, Object> requestContext) throws SubscriptionException {

        assert (userId !=null);
        assert (requestContext != null);

        if (requestContext.get(CONTEXT_CATAPULT_SPORTS) != null) {
            AuthContext authContext = objectMapper.convertValue(requestContext.get(CONTEXT_CATAPULT_SPORTS), AuthContext.class);

            //validating userId
            if (authContext.getSubject() == null || !authContext.getSubject().equalsIgnoreCase(userId)) {
                throw new SubscriptionException("Unauthorized user");
            }

            //validating LDS scope
            if(!authContext.containsScope(CONTEXT_LDS_SCOPE)){
                throw new SubscriptionException("User does not have permission to access live data");
            }
        }
    }

}
