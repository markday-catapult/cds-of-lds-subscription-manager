package com.catapult.lds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
     * @throws UnauthorizedUserException
     */
    @Override
    public void validateClaims(String userId, Map<String, Object> requestContext) throws UnauthorizedUserException, SubscriptionException {

        assert (userId !=null);
        assert (requestContext != null);

        //TODO need to throw UnauthorizedUserException when requestContext doesnot exist.
        if (requestContext.get(CONTEXT_CATAPULT_SPORTS) != null) {

            AuthContext authContext = null;
            try {
                authContext = objectMapper.readValue(String.valueOf(requestContext.get(CONTEXT_CATAPULT_SPORTS)), AuthContext.class);
            } catch (JsonProcessingException e) {
                logger.error("Unable to parse auth context:{}",e.getMessage());
                throw new SubscriptionException("Unable to parse auth context");
            }

            //validating userId
            if (authContext.getSubject() == null || !authContext.getSubject().equalsIgnoreCase(userId)) {
                logger.error("Invalid user:{}",userId);
                throw new UnauthorizedUserException("Unauthorized user");
            }

            //validating LDS scope
            if(!authContext.containsScope(CONTEXT_LDS_SCOPE)){
                logger.error("Invalid scope: LDS scope not available");
                throw new UnauthorizedUserException("User does not have permission to access live data");
            }
        }
    }

}
