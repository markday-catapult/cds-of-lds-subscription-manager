package com.catapult.lds.service;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * {@code Subscription} is an immutable.... TBD
 */
public class Subscription {

    private final String id;
    private final String connectionId;
    private final List<String> resources;

    public Subscription(String connectionId, List<String> resources) {
        assert connectionId != null;
        assert !resources.isEmpty();

        this.id = UUID.randomUUID().toString();
        this.connectionId = connectionId;
        this.resources = List.copyOf(resources);
    }

    public Subscription(String connectionId, String id, String resourceListJson) {
        assert connectionId != null;
        assert resourceListJson != null;

        this.id = id;
        this.connectionId = connectionId;
        this.resources =
                StreamSupport.stream(new JSONArray(resourceListJson).spliterator(), false)
                        .map(o -> o.toString())
                        .collect(Collectors.toList());
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
     * Returns the list of resources ids for this subscription.  These resource ids will already * have the keyspace
     * prefix applied to them.
     *
     * @post !return.isEmpty()
     */
    public List<String> getResources() {
        return new ArrayList<>(this.resources);
    }

    /**
     * Returns a list of resource ids for this subscription, in stringified json format.  The resource ids will already
     * have the keyspace prefix applied to them.
     * <p/>
     * <code>["U-user-id-1", "D-device-id-3", "D-device-id-7"]</code>
     *
     * @post return != null
     */
    public String getResourceListJson() {
        return org.json.simple.JSONArray.toJSONString(this.getResources());
    }
}
