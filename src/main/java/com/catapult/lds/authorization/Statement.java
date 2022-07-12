package com.catapult.lds.authorization;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * {@code Statement} is a container class that can be serialized into an AWS IAM Statement.
 */
@JsonDeserialize(builder = Statement.Builder.class)
public class Statement {

    public final String Action = "execute-api:Invoke";

    public String Effect;
    public String Resource;

    private Statement(Builder builder) {
        this.Effect = builder.effect;
        this.Resource = builder.resource;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String effect;
        private String resource;

        private Builder() {
        }

        public Builder effect(String effect) {
            this.effect = effect;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Statement build() {
            return new Statement(this);
        }
    }
}
