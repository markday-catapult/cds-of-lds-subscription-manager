package com.catapult.lds.service;

import java.util.Map;

/**
 * {@code AuthorizationService} provides methods for validating Security  token.
 */
public interface AuthorizationService {
    public void validateJWTToken(String username, Map<String, Object> requestContext) throws SubscriptionException;
}
