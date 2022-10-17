package com.catapult.lds.service;

import java.util.Map;

/**
 * {@code ClaimsValidationService} provides methods for validating JWT token claims available in the lambda request context.
 */
public interface SubscriptionAuthorizationService {
    /**
     * Validates the userId in the subscription request is same as the subject in JWT .
     * Also validates if LDS claim is available in JWT token and throws an exception if validation fails.
     *
     * @throws SubscriptionException
     * @throws UnauthorizedUserException
     * @pre userId != null
     * @pre requestContext != null
     */
    void validateClaims(String username, Map<String, Object> requestContext) throws UnauthorizedUserException, SubscriptionException;
}
