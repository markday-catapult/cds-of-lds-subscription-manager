package com.catapult.lds.service;

/**
 * {@code SubscriptionAuthorizationService} provides methods for checking claims available in the authorization
 * context.
 */
public interface SubscriptionAuthorizationService {

    /**
     * Performs an authorization check by making sure that {@linkplain AuthContext#getSubject authenticated user} can
     * view data attributed to the given user id
     *
     * @throws UnauthorizedUserException if data expected to be in the given auth context is not present orif the
     *                                   {@linkplain AuthContext#getSubject authenticated user} does not have access to
     *                                   the data attributed to the given user id.
     * @pre userId != null
     * @pre authContext != null
     */
    void checkAuthorizationForUserResource(String userId, AuthContext authContext) throws UnauthorizedUserException, SubscriptionException;
}
