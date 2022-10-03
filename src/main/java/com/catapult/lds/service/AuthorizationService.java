package com.catapult.lds.service;

/**
 * {@code AuthorizationService} provides method for validating security tokens
 */
public interface AuthorizationService {

    /**
     * Validates the provided JWT token
     *
     * @throws SubscriptionException if there is an error while validating token
     * @pre token != null
     *
     */
    String  authorizeToken(String token) throws SubscriptionException;
}
