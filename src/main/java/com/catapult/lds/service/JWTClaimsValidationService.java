package com.catapult.lds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
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
import java.util.Map;
import java.util.Set;

/**
 * {@code JWTValidationService} provides methods for validating Open field Micro auth JWT claims set in Lambda request context.
 */
public class JWTClaimsValidationService implements ClaimsValidationService {


    /**
     * The name of the environment variable which has a value of open field micro auth resource check endpoint
     */
    private static final String LDS_OF_MICROAUTH_RESOURCE_CHECK_ENDPOINT_ENV = "LDS_OF_MICROAUTH_RESOURCE_CHECK_ENDPOINT";

    private static String resourceCheckEndpoint = null;

    /**
     * Key in the request context map which has authorizer data as value
     */
    private static final String CONTEXT_CATAPULT_SPORTS = "catapultsports";

    /**
     * LDS scope in the JWT claim used to check access permission.
     */
    private static final String CONTEXT_LDS_SCOPE = "com.catapultsports.services.LDS";

    /**
     * The logger used by this handler.
     *
     * @invariant logger != null
     */
    private final Logger logger = LoggerFactory.getLogger(JWTClaimsValidationService.class);

    /**
     * The object mapper used by this service.
     *
     * @invariant objectMapper != null
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Httpclient used by the service to make a call to Open Field microauth resource check endpoint
     *
     * @invariant httpClient != null
     */
    private static CloseableHttpClient httpClient = null;

    /**
     * Creates a new {@code JWTClaimsValidationService}.
     */
    public JWTClaimsValidationService(){
        JWTClaimsValidationService.httpClient = HttpClientBuilder.create().build();
        JWTClaimsValidationService.resourceCheckEndpoint = System.getenv(LDS_OF_MICROAUTH_RESOURCE_CHECK_ENDPOINT_ENV);
    }

    /**
     * Creates a new {@code JWTClaimsValidationService} with the given http client and endpoint.
     * <p>
     * This constructor is used by Junit tests only.
     *
     * @pre httpClient != null
     * @pre resourceCheckEndpoint != null
     */
    public JWTClaimsValidationService(CloseableHttpClient httpClient,String resourceCheckEndpoint){
        assert (httpClient != null);
        assert (resourceCheckEndpoint != null);

        JWTClaimsValidationService.httpClient =httpClient;
        JWTClaimsValidationService.resourceCheckEndpoint = resourceCheckEndpoint;
    }


    /**
     * {@inheritDoc}
     *
     * @pre userId != null
     * @pre requestContext != null
     * @throws SubscriptionException
     * @throws UnauthorizedUserException
     */
    @Override
    public void validateClaims(String userId, Map<String, Object> requestContext) throws UnauthorizedUserException, SubscriptionException {

        assert (userId !=null);
        assert (requestContext != null);

        //TODO need to throw SubscriptionException when requestContext doesnot exist.
        if (requestContext.get(CONTEXT_CATAPULT_SPORTS) != null) {

            AuthContext authContext;
            String authContextString = null;
            try {
                authContextString = String.valueOf(requestContext.get(CONTEXT_CATAPULT_SPORTS));
                authContext = objectMapper.readValue(authContextString, AuthContext.class);
            } catch (JsonProcessingException e) {
                logger.error("Unable to parse auth context {}:Error {}",authContextString,e.getMessage());
                throw new SubscriptionException("Unable to parse auth context");
            }

            if(authContext.getSubject() == null || authContext.getToken() == null || authContext.getAuth().getClaims() ==null){
                throw new SubscriptionException("Unable to validate user permissions");
            }

            //validating user permissions
            if(!validUserPermissions(authContext.getSubject(),userId,authContext.getToken())){
                throw new UnauthorizedUserException("User does not have permission to access the subscribed resources");
            }

            //validating LDS scope
            if(!authContext.containsScope(CONTEXT_LDS_SCOPE)){
                logger.error("Invalid scope: LDS scope not available");
                throw new UnauthorizedUserException("User does not have permission to access live data");
            }
        }
    }

    private boolean validUserPermissions(String subscribingUserId, String subscribedUserResourceId, String authToken){

        //Returning true if subscriber is same as subscribed resource owner
        if(subscribingUserId.equalsIgnoreCase(subscribedUserResourceId)){
            return true;
        }

        //Making http call to the microauth resource check endpoint
        CloseableHttpResponse response;
        try {

            HttpPost httpPost = new HttpPost(resourceCheckEndpoint);

            StringEntity entity = new StringEntity(objectMapper.writeValueAsString(ResourceCheckRequest.builder()
                    .user(Set.of(subscribingUserId,subscribedUserResourceId)).build()));
            entity.setContentType(String.valueOf(ContentType.APPLICATION_JSON));
            httpPost.setEntity(entity);
            httpPost.setHeader("Authorization","Bearer "+authToken);
            response = httpClient.execute(httpPost);

        } catch (UnsupportedEncodingException | JsonProcessingException e) {
            logger.error("Error validating user permissions, Error while creating request :{}",e);
            return false;
        } catch (IOException e) {
            logger.error("Error validating user permissions {}",e);
            return false;
        }

        //Validating the relationship between subscriber userId and subscribed resource userId
        try {
            if(response.getStatusLine().getStatusCode() == 200) {
                String resourceCheckResponseString = EntityUtils.toString(response.getEntity());
                ResourceCheckResponse resourceCheckResponse = objectMapper.readValue(resourceCheckResponseString,ResourceCheckResponse.class);

                // Checking if the read attribute of the subscribed resource userId is true in the response
                return resourceCheckResponse.getUser().stream()
                        .filter(user->user.getIdentifier().equals(subscribedUserResourceId) &&user.isRead())
                        .findFirst()
                        .isPresent();

            }else{
                logger.error("Resource check call gave a non 200 response: Http Status code {}", response.getStatusLine().getStatusCode() );
                return false;
            }

        } catch (IOException e) {
            logger.error("Error validating resource check response {}",e);
            return false;
        }
    }

    @Data
    @Builder
    public static  class ResourceCheckRequest {
        private Set<String > user = null ;

    }
    @Data
    public static class ResourceCheckResponse{
        private List<ResourceIdentifier> user;

    }
    @Data
    public static class ResourceIdentifier{
        private String type;
        private String identifier;
        private boolean read;

    }

}
