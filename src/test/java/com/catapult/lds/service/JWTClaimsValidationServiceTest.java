package com.catapult.lds.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class JWTClaimsValidationServiceTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidateJWTToken() throws IOException, SubscriptionException, UnauthorizedUserException {

        Path path = Paths.get("src/test/resources/authcontext.json");
        String jsonData = Files.lines(path).collect(joining("\n"));
        AuthContext context = mapper.readValue(jsonData, AuthContext.class);
        Map<String,Object> requestContext = Map.of("catapultsports",mapper.writeValueAsString(context));
        JWTClaimsValidationService jwtValidationService = new JWTClaimsValidationService();
        jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext);

        Assert.assertThrows(UnauthorizedUserException.class,()->jwtValidationService.validateClaims("invalidUserId",requestContext));

        context.getAuth().getClaims().setScopes(new HashSet<>());
        Map<String,Object> requestContext1 = Map.of("catapultsports",mapper.writeValueAsString(context));
        Assert.assertThrows(UnauthorizedUserException.class,()->jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext1));

        context.getAuth().getClaims().setSub(null);
        Map<String,Object> requestContext2 = Map.of("catapultsports",mapper.writeValueAsString(context));
        Assert.assertThrows(SubscriptionException.class,()->jwtValidationService.validateClaims("ee8758ec-fe5f-4574-8b71-ba24f30ee672",requestContext2));

    }


}