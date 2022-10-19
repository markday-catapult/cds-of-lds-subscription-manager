package com.catapult.lds.service;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;
import java.util.UUID;

/**
 * {@code Subscription} is an immutable.... TBD
 */
@Value
@Jacksonized
@Builder
public class Subscription {

    /**
     * The id of this subscription.
     *
     * @invariant id != null
     */
    @Builder.Default
    @NonNull
    private String id = UUID.randomUUID().toString();

    /**
     * The connection id for this subscription
     *
     * @invariant connectionId != null
     */
    @NonNull
    private String connectionId;

    /**
     * The set of resources ids for this subscription.  These resource ids will already have the keyspace prefix
     * applied to them.
     *
     * @invariant resources != null
     */
    @NonNull
    @Singular
    private Set<String> resources;

    /**
     * The sample rate for this subscription.  If null, sampling should not be done.
     */
    private Integer sampleRate;

}
