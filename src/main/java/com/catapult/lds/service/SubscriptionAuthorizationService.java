package com.catapult.lds.service;

/**
 * {@code SubscriptionAuthorizationService} provides methods for checking claims available in the authorization context.
 */
public interface SubscriptionAuthorizationService {

    /**
     * Performs an authorization check by making sure that {@link AuthContext#getSubject()} can view data attributed to the given userId
     *
     * @throws SubscriptionException     if data expected to be in the given request context is not present
     * @throws UnauthorizedUserException if the principal identified by the jwt sub claim does not have access to the data
     *                                   attributed to the given user id.
     * @pre userId != null
     * @pre authContext != null
     */
    void checkAuthorizationForUserResource(String userId, AuthContext authContext) throws UnauthorizedUserException, SubscriptionException;
}
