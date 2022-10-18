package com.catapult.lds.service;

import java.util.Map;

/**
 * {@code SubscriptionAuthorizationService} provides methods for checking claims available in the lambda request context.
 */
public interface SubscriptionAuthorizationService {
    /**
     * Performs an authorization check by making sure the principal (JWT sub in the request context) can view data attributed
     *
     * @throws SubscriptionException     if data expected to be in the given request context is not present
     * @throws UnauthorizedUserException if the principal identified by the jwt sub claim does not have access to the data
     *                                   attributed to the given user id.
     * @pre userId != null
     * @pre requestContext != null
     */
    void checkAuthorizationForUserResource(String userId, Map<String, Object> requestContext) throws UnauthorizedUserException, SubscriptionException;
}
