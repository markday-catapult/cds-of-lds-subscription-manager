package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
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

    public String getSubject(){
        return auth != null && auth.claims != null && auth.claims.sub != null ? auth.claims.getSub() : null;
    }
    public String getToken(){
        return auth != null && auth.token != null ? auth.getToken():null;
    }
    public boolean containsScope(String scopeName){
        return auth != null && auth.claims != null && auth.claims.scopes!=null?
                auth.claims.getScopes().stream().filter(s->s.equalsIgnoreCase(scopeName)).findFirst().isPresent():false;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Auth {
        private Claims claims;
        private String token;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Claims {
        private String sub;
        private Set<String> scopes;
    }

}
