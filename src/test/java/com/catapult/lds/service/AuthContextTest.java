package com.catapult.lds.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class AuthContextTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testExtractContext() throws IOException, SubscriptionException {

        Path path = Paths.get("src/test/resources/authcontext.json");
        String jsonData = Files.lines(path).collect(joining("\n"));
        AuthContext context = mapper.readValue(jsonData, AuthContext.class);
        Map<String, Object> requestContext = Map.of("catapultsports", mapper.writeValueAsString(context));
        AuthContext authContext = AuthContext.extractContext(requestContext);
        Assert.assertEquals(authContext.getSubject(), "ee8758ec-fe5f-4574-8b71-ba24f30ee672");
        Assert.assertTrue(authContext.containsScope("com.catapultsports.services.lds"));
        Assert.assertTrue(authContext.containsScope("com.catapultsports.services.LDS"));
        Assert.assertNotNull(authContext.getToken());

    }
}