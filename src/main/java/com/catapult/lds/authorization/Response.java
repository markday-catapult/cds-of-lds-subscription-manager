package com.catapult.lds.authorization;

import com.catapult.lds.ConnectAuthorizationHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Map;

/**
 * {@code Response} is a response from the {@link ConnectAuthorizationHandler} either allowing or denying the creation
 * of a websocket connection.
 */
@JsonDeserialize(builder = Response.Builder.class)
public class Response {

    @JsonProperty("principalId")
    private String principalId;

    @JsonProperty("policyDocument")
    private PolicyDocument policyDocument;

    @JsonProperty("context")
    private Map<String, String> context;

    private Response(Builder builder) {
        this.principalId = builder.principalId;
        this.policyDocument = builder.policyDocument;
        this.context = builder.context;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public PolicyDocument getPolicyDocument() {
        return policyDocument;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String principalId;
        private PolicyDocument policyDocument;
        private Map<String, String> context;

        private Builder() {
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder policyDocument(PolicyDocument policyDocument) {
            this.policyDocument = policyDocument;
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context;
            return this;
        }

        public Response build() {
            return new Response(this);
        }
    }
}