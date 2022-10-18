package com.catapult.lds.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.stream.Collectors.joining;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubscriptionAuthorizationServiceImplTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidateJWTToken() throws IOException, SubscriptionException, UnauthorizedUserException {


        Path path = Paths.get("src/test/resources/authcontext.json");
        String jsonData = Files.lines(path).collect(joining("\n"));
        AuthContext context = mapper.readValue(jsonData, AuthContext.class);

        CloseableHttpClient httpClientMock = mock(CloseableHttpClient.class);

        Path path1 = Paths.get("src/test/resources/microauthresourcecheckresponse.json");
        String jsonData1 = Files.lines(path1).collect(joining("\n"));
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        HttpEntity httpEntity = mock(HttpEntity.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(closeableHttpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(jsonData1.getBytes()));


        when(httpClientMock.execute(any(HttpPost.class))).thenReturn(closeableHttpResponse);
        SubscriptionAuthorizationServiceImpl jwtValidationService = new SubscriptionAuthorizationServiceImpl(httpClientMock, "dummyEndpoint");

        //subscriber abd subscriber resource userId are the same
        jwtValidationService.checkAuthorizationForUserResource("ee8758ec-fe5f-4574-8b71-ba24f30ee672", context);

        //subscriber and subscriber resource userId belongs to the same account
        jwtValidationService.checkAuthorizationForUserResource("validUserId", context);
    }


}