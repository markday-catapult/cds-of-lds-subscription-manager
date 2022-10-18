package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 * {@code AuthContext} is a representation of JWT claims available in lambda request context.
 */
@Builder
@Jacksonized
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuthContext {

    private Auth auth;

    /**
     * Returns the sub (user) claim in the JWT Authentication token if available, otherwise returns null
     */
    public String getSubject() {
        return auth != null && auth.claims != null && auth.claims.sub != null ? auth.claims.getSub() : null;
    }

    /**
     * Returns the JWT Authentication token if available, otherwise returns null
     */
    public String getToken() {
        return auth != null && auth.token != null ? auth.getToken() : null;
    }

    /**
     * Returns true if the provided scopeName is present in the list of scopes available in the context
     */
    public boolean containsScope(String scopeName) {
        return auth != null && auth.claims != null && auth.claims.scopes != null ?
                auth.claims.getScopes().stream().filter(s -> s.equalsIgnoreCase(scopeName)).findFirst().isPresent() : false;
    }

    /**
     * {@code Auth} is the representation of Auth attribute in the request context
     */
    @Builder
    @Jacksonized
    @Value
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Auth {
        private Claims claims;
        private String token;

    }

    /**
     * {@code Claims} is the representation of Claim attribute in the {@code Auth}
     */
    @Builder
    @Jacksonized
    @Value
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Claims {
        private String sub;
        private Set<String> scopes;
    }

}
