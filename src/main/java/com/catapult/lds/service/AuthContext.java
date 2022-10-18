package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * {@code AuthContext} is a representation of JWT claims available in lambda request context.
 */
@Builder
@Jacksonized
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuthContext {

    /**
     * Key in the request context map which has authorizer data as value
     */
    private static final String CONTEXT_CATAPULT_SPORTS = "catapultsports";

    /**
     * The logger used by this class.
     *
     * @invariant logger != null
     */
    private static final Logger logger = LoggerFactory.getLogger(AuthContext.class);

    /**
     * The object mapper used by this class.
     *
     * @invariant objectMapper != null
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Auth auth;

    /**
     * Extracts {@code AuthContext} from the give request context map
     *
     * @throws SubscriptionException if request context is empty,null or data cannot be parsed
     */
    public static AuthContext extractContext(Map<String, Object> requestContext) throws SubscriptionException {

        if (requestContext == null || requestContext.isEmpty()) {
            logger.error("Request context is null or empty");
            throw new SubscriptionException("Request context is null or empty.Unable to check user permissions");
        }

        final AuthContext authContext;
        String authContextString = null;
        try {
            authContextString = String.valueOf(requestContext.get(CONTEXT_CATAPULT_SPORTS));
            authContext = objectMapper.readValue(authContextString, AuthContext.class);
        } catch (JsonProcessingException e) {
            logger.error("Unable to parse auth context {}: Error {}", authContextString, e.getMessage());
            throw new SubscriptionException("Unable to parse auth context");
        }
        return authContext;
    }

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
     * Returns true if the given scopeName is present in the list of scopes available in the context
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
