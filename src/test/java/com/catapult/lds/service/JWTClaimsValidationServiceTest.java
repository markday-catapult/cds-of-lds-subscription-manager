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

public class JWTClaimsValidationServiceTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidateJWTToken() throws IOException, SubscriptionException {

        Path path = Paths.get("src/test/resources/authcontext.json");
        String jsonData = Files.lines(path).collect(joining("\n"));
        Map<String,Object> requestContext = mapper.readValue(jsonData, Map.class);
        JWTClaimsValidationService jwtValidationService = new JWTClaimsValidationService();
        jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext);
        Assert.assertThrows(SubscriptionException.class,()->jwtValidationService.validateClaims("invalidUserId",requestContext));

    }

}