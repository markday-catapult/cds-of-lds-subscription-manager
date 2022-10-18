package com.catapult.lds.service;

import java.util.Map;

/**
 * {@code SubscriptionAuthorizationService} provides methods for checking claims available in the lambda request context.
 */
public interface SubscriptionAuthorizationService {
    /**
     * Performs an authorization check by making sure the the principal (JWT sub in the request context) can view data attributed
     * to the given user id.
     *
     * @throws SubscriptionException
     * @throws UnauthorizedUserException
     * @pre userId != null
     * @pre requestContext != null
     */
    void validateClaims(String userId, Map<String, Object> requestContext) throws UnauthorizedUserException, SubscriptionException;
}
