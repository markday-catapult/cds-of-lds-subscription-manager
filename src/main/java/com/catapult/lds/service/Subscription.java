package com.catapult.lds.service;

import java.util.UUID;

/**
 * {@code Subscription} is an immutable.... TBD
 */
public class Subscription {

    private String id = UUID.randomUUID().toString();
    private long createdAt = System.currentTimeMillis();
    private String connectionId;
    private String resourceIdsJson;

    public Subscription(String connectionId) {
        this.connectionId = connectionId;
        this.resourceIdsJson = "{}";
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
     * Returns the timestamp of when this subscription was created.
     *
     * @post return > 0
     */
    public long getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Returns a list of resource ids for this subscription, in stringified json format.  The resource ids will already
     * have the keyspace prefix applied to them.
     * <p/>
     * <code>["U-user-id-1", "D-device-id-3", "D-device-id-7"]</code>
     *
     * @post return != null
     */
    public String getResourceIdsJson() {
        return this.resourceIdsJson;
    }
}
