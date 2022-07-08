package com.catapult.lds.service;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * {@code Subscription} is an immutable.... TBD
 */
public class Subscription {

    private final String id;
    private final String connectionId;
    private final Set<String> resources;

    public Subscription(String connectionId, Set<String> resources) {
        assert connectionId != null;
        assert !resources.isEmpty();

        this.id = UUID.randomUUID().toString();
        this.connectionId = connectionId;
        this.resources = Set.copyOf(resources);
    }

    public Subscription(String connectionId, String id, String resourceListJson) {
        assert connectionId != null;
        assert resourceListJson != null;

        this.id = id;
        this.connectionId = connectionId;
        this.resources =
                StreamSupport.stream(new JSONArray(resourceListJson).spliterator(), false)
                        .map(Object::toString)
                        .collect(Collectors.toSet());
    }

    /**
     * Returns the id of this subscription
     *
     * @post return != null
     */
    public String getId() {
        return this.id;
    }

    /**
     * Returns the connection id for this subscription
     *
     * @post return != null
     */
    public String getConnectionId() {
        return this.connectionId;
    }

    /**
     * Returns the set of resources ids for this subscription.  These resource ids will already * have the keyspace
     * prefix applied to them.
     *
     * @post !return.isEmpty()
     */
    public Set<String> getResources() {
        return new HashSet<>(this.resources);
    }
}
