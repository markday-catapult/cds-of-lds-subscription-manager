package com.catapult.lds.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;

import static java.util.stream.Collectors.joining;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JWTClaimsValidationServiceTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidateJWTToken() throws IOException, SubscriptionException, UnauthorizedUserException {


        Path path = Paths.get("src/test/resources/authcontext.json");
        String jsonData = Files.lines(path).collect(joining("\n"));
        AuthContext context = mapper.readValue(jsonData, AuthContext.class);
        Map<String,Object> requestContext = Map.of("catapultsports",mapper.writeValueAsString(context));

        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);

        Path path1 = Paths.get("src/test/resources/microauthresourcecheckresponse.json");
        String jsonData1 = Files.lines(path1).collect(joining("\n"));
        JWTClaimsValidationService.ResourceCheckResponse resourceCheckResponse = mapper.readValue(jsonData1, JWTClaimsValidationService.ResourceCheckResponse.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        HttpEntity httpEntity = mock(HttpEntity.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(closeableHttpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream( jsonData1.getBytes()));


        when(httpClientMock.execute(any(HttpPost.class))).thenReturn(closeableHttpResponse);
        JWTClaimsValidationService jwtValidationService = new JWTClaimsValidationService(httpClientMock,"dummyEndpoint");

        //subscriber abd subscriber resource userId are the same
        jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext);

        //subscriber and subscriber resource userId belongs to the same account
        jwtValidationService.validateClaims("validUserId",requestContext);

        //LDS scope not available
        context.getAuth().getClaims().setScopes(new HashSet<>());
        Map<String,Object> requestContext1 = Map.of("catapultsports",mapper.writeValueAsString(context));
        Assert.assertThrows(UnauthorizedUserException.class,()->jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext1));

        //Subject not available in context
        context.getAuth().getClaims().setSub(null);
        Map<String,Object> requestContext2 = Map.of("catapultsports",mapper.writeValueAsString(context));
        Assert.assertThrows(SubscriptionException.class,()->jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext2));

    }


}