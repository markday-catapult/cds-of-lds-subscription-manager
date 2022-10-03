package com.catapult.lds.service;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * {@code AuthorizationService } provides method for validating JWT token
 */
public class AuthorizationService {
    
    /**
     * Validates the provided JWT token
     *
     * @throws SubscriptionException if there is an error while validating JWT token
     * @pre token != null
     *
     */
    public String  authorizeToke(String token) throws SubscriptionException {

        assert (token != null);
        
        DecodedJWT jwt = JWT.decode(token);

        //TODO: Extract the url from jwt
        // JWKProvider appends the well know path to the host "https://backend-au.openfield.catapultsports.com/.well-known/jwks.json"
        JwkProvider provider = new UrlJwkProvider("https://backend-au.openfield.catapultsports.com");

        // Extracting the key from JWKS
        Jwk jwk = null;
        try {
            jwk = provider.get(jwt.getKeyId());
        } catch (JwkException e) {
            throw new SubscriptionException("Error getting JWKS for JWT validation: "+e.getMessage());
        }
        //Extracting public key from JWK
        Algorithm algorithm = null;
        try {
            algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
        } catch (InvalidPublicKeyException e) {
            throw new SubscriptionException("Error getting Public key from JWKS: "+e.getMessage());
        }
        //Verifying the token
        try {
            algorithm.verify(jwt);
        }catch (SignatureVerificationException e){
            throw new SubscriptionException("Invalid JWT Token");
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        return new String(decoder.decode(jwt.getPayload()));

    }
}
