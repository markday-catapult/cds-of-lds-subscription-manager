package com.catapult.lds.service;

import java.util.Map;

/**
 * {@code AuthorizationService} provides methods for validating Security  token.
 */
public interface AuthorizationService {
    /**
     * * Validates the userId in the subscription request is same as the subject in JWT . Also validates if LDS claim is available in JWT token and throws an exception if validation fails.
     */
    public void validateJWTToken(String username, Map<String, Object> requestContext) throws SubscriptionException;
}
