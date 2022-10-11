package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
/**
 * {@code AuthContext} is a representation of JWT claims available in lambda request context.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthContext {

    private Auth auth;

    public String getSubject(){
        return this.auth.claims.getSub();
    }
    public boolean containsScope(String scopeName){
        return this.auth.claims.getScopes().contains(scopeName);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Auth {
        private Claims claims;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Claims {
        private String sub;
        private List<String> scopes;
    }

}
