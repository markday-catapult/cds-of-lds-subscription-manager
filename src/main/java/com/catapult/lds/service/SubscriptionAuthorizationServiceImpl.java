package com.catapult.lds.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;

/**
 * {@code JWTValidationService} provides methods for validating Open field Micro auth JWT claims set in Lambda request
 * context.
 */
public class SubscriptionAuthorizationServiceImpl implements SubscriptionAuthorizationService {

    /**
     * The name of the environment variable which has a value of open field micro auth resource check endpoint
     */
    private static final String LDS_OF_MICROAUTH_RESOURCE_CHECK_ENDPOINT_ENV = "LDS_OF_MICROAUTH_RESOURCE_CHECK_ENDPOINT";
    /**
     * LDS scope in the JWT claim used to check access permission.
     */
    private static final String CONTEXT_LDS_SCOPE = "com.catapultsports.services.LDS";
    /**
     * The object mapper used by this service.
     *
     * @invariant objectMapper != null
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * API endpoint for Micro auth resource check
     */
    private static String resourceCheckEndpoint = null;
    /**
     * Httpclient used by the service to make a call to Open Field microauth resource check endpoint
     *
     * @invariant httpClient != null
     */
    private static CloseableHttpClient httpClient = null;
    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(SubscriptionAuthorizationServiceImpl.class);

    /**
     * Creates a new {@code JWTClaimsValidationService}.
     */
    public SubscriptionAuthorizationServiceImpl() {
        SubscriptionAuthorizationServiceImpl.httpClient = HttpClientBuilder.create().build();
        SubscriptionAuthorizationServiceImpl.resourceCheckEndpoint = System.getenv(LDS_OF_MICROAUTH_RESOURCE_CHECK_ENDPOINT_ENV);
    }

    /**
     * Creates a new {@code JWTClaimsValidationService} with the given http client and endpoint.
     * <p>
     * This constructor is used by Junit tests only.
     *
     * @pre httpClient != null
     * @pre resourceCheckEndpoint != null
     */
    public SubscriptionAuthorizationServiceImpl(CloseableHttpClient httpClient, String resourceCheckEndpoint) {
        assert (httpClient != null);
        assert (resourceCheckEndpoint != null);

        SubscriptionAuthorizationServiceImpl.httpClient = httpClient;
        SubscriptionAuthorizationServiceImpl.resourceCheckEndpoint = resourceCheckEndpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkAuthorizationForUserResource(String userId, AuthContext authContext) throws UnauthorizedUserException {

        assert (userId != null);
        assert (authContext != null);

        if (authContext.getSubject() == null) {
            throw new UnauthorizedUserException("Unable to validate user permissions, Subject not found");
        }
        if (authContext.getToken() == null) {
            throw new UnauthorizedUserException("Unable to validate user permissions, Token not found");
        }
        if (authContext.getAuth() == null || authContext.getAuth().getClaims() == null) {
            throw new UnauthorizedUserException("Unable to validate user permissions, Claims not found");
        }

        // validating LDS scope
        if (!authContext.containsScope(CONTEXT_LDS_SCOPE)) {
            this.logger.error("Invalid scope: LDS scope not available");
            throw new UnauthorizedUserException("User does not have permission to access live data");
        }

        // checking user permissions
        if (!this.checkPermissionsOnUserResource(authContext.getSubject(), userId, authContext.getToken())) {
            throw new UnauthorizedUserException("User does not have permission to access the subscribed resources");
        }

    }

    /**
     * returns true if the given jwtSub has access to a live data stream attributed to the given subscribed user
     * resource id
     *
     * @pre jwtSub != null
     * @pre subscribedUserResourceId != null
     * @pre authToken!= null
     */
    private boolean checkPermissionsOnUserResource(String jwtSub, String subscribedUserResourceId, String authToken) {

        assert (jwtSub != null);
        assert (subscribedUserResourceId != null);
        assert (authToken != null);

        // Return true if subscriber is same as subscribed resource owner
        if (jwtSub.equalsIgnoreCase(subscribedUserResourceId)) {
            return true;
        }

        // Making http call to the microauth resource check endpoint
        final CloseableHttpResponse response;
        try {

            HttpPost httpPost = new HttpPost(resourceCheckEndpoint);

            StringEntity entity = new StringEntity(objectMapper.writeValueAsString(ResourceCheckRequest.builder()
                    .user(Set.of(subscribedUserResourceId)).build()));
            entity.setContentType(String.valueOf(ContentType.APPLICATION_JSON));
            httpPost.setEntity(entity);
            httpPost.setHeader("Authorization", "Bearer " + authToken);
            response = httpClient.execute(httpPost);

        } catch (UnsupportedEncodingException | JsonProcessingException e) {
            this.logger.error("Error creating request to check permissions on resource", e);
            return false;
        } catch (IOException e) {
            this.logger.error("Error checking permissions on resource", e);
            return false;
        }

        // Validating the relationship between subscriber userId and subscribed resource userId
        try {
            if (response.getStatusLine().getStatusCode() == 200) {
                String resourceCheckResponseString = EntityUtils.toString(response.getEntity());
                ResourceCheckResponse resourceCheckResponse = objectMapper.readValue(resourceCheckResponseString, ResourceCheckResponse.class);

                // Checking if the read attribute of the subscribed resource userId is true in the response
                return resourceCheckResponse.getUserIds().stream()
                        .anyMatch(user -> user.getIdentifier().equals(subscribedUserResourceId) && user.isRead());

            } else {
                this.logger.error("Resource check call gave a non 200 response: Http Status code {}", response.getStatusLine().getStatusCode());
                return false;
            }

        } catch (IOException e) {
            this.logger.error("Error validating resource check response ", e);
            return false;
        }
    }

    /**
     * POJO class representing the micro auth resource check request
     */
    @Builder
    @Jacksonized
    @Value
    public static class ResourceCheckRequest {

        /**
         * A set of user ids for which relationship needs to be checked.
         */
        Set<String> user;

    }

    /**
     * POJO class representing the micro auth resource check response
     */
    @Builder
    @Jacksonized
    @Value
    public static class ResourceCheckResponse {

        @JsonProperty("user")
        List<ResourceIdentifier> userIds;

    }

    /**
     * POJO class representing individual node in {@code ResourceCheckResponse}
     */
    @Builder
    @Jacksonized
    @Value
    public static class ResourceIdentifier {

        String type;
        String identifier;
        boolean read;
    }

}
