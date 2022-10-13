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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code JWTValidationService} provides methods for validating Open field Micro auth JWT claims set in Lambda request context.
 */
public class JWTClaimsValidationService implements ClaimsValidationService {


    private static final String CONTEXT_CATAPULT_SPORTS = "catapultsports";
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

    private static final CloseableHttpClient httpClient = HttpClientBuilder.create().build();

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

            //validating user permissions
            if(!validateUserPermissions(authContext.getSubject(),userId,authContext.getToken())){
                throw new UnauthorizedUserException("User does not have permission to access the subscribed resources");
            }

            //validating LDS scope
            if(!authContext.containsScope(CONTEXT_LDS_SCOPE)){
                logger.error("Invalid scope: LDS scope not available");
                throw new UnauthorizedUserException("User does not have permission to access live data");
            }
        }
    }

    private boolean validateUserPermissions(String subscribingUser,String subscribedUserResource,String authToken){


        //Returning true if subscriber is same as subscribed resource owner
        if(subscribingUser.equalsIgnoreCase(subscribedUserResource)){
            return true;
        }

        //Validating the relationship between subscriber and subscribed resource owner
        CloseableHttpResponse response;
        try {

            HttpPost httpPost = new HttpPost("https://au.catapultsports.com/api/v6/microauth/resources/check");
            String resourceCheckRequest = objectMapper.writeValueAsString(ResourceCheckRequest.builder().users(Set.of(subscribingUser,subscribedUserResource)).build());
            StringEntity entity = new StringEntity(resourceCheckRequest);
            entity.setContentType(String.valueOf(ContentType.APPLICATION_JSON));
            entity.setContentEncoding("gzip, deflate, br");
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setHeader("Authorization","Bearer "+authToken);
            httpPost.setHeader("Accept-Encoding", "gzip, deflate, br");
           /* EntityBuilder builder = EntityBuilder.create();
            builder.setContentType(ContentType.APPLICATION_JSON);
            builder.setText(resourceCheckRequest);
            //builder.setContentEncoding("gzip, deflate, br");
            httpPost.setEntity(builder.build());
            httpPost.setHeader("Authorization","Bearer "+authToken);*/
           // httpPost.setHeader("Accept","*/*");

            response = httpClient.execute(httpPost);
        } catch (UnsupportedEncodingException | JsonProcessingException e) {
            logger.error("Error validating user permissions, Error while creating request :{}",e.getMessage());
            return false;
        } catch (IOException e) {
            logger.error("Error validating user permissions {}",e.getMessage());
            return false;
        }

        try {

            if(response.getStatusLine().getStatusCode() == 200) {
                String resourceCheckResponseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                ResourceCheckResponse resourceCheckResponse = objectMapper.readValue(resourceCheckResponseString,ResourceCheckResponse.class);
                return resourceCheckResponse.getUser().stream().filter(user-> !user.isRead()).findFirst().isPresent();

            }else{
                logger.error("Resource check call gave a non 200 response");
                return false;
            }

        } catch (IOException e) {
            logger.error("Error validating resource check response {}",e.getMessage());
            return false;
        }
    }

    @Data
    @Builder
    public static  class ResourceCheckRequest {
        private Set<String > users = null ;

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
