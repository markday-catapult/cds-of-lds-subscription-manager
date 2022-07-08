package com.catapult.lds.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimpleCacheService implements SubscriptionCacheService {

    public static final SubscriptionCacheService instance = new SimpleCacheService();

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void createConnection(String connectionId) throws SubscriptionException {
    }

    @Override
    public boolean connectionExists(String connectionId) {
        return true;
    }

    @Override
    public void closeConnection(String connectionId) throws SubscriptionException {

    }

    @Override
    public void putSubscription(Subscription subscription) throws SubscriptionException {

    }

    @Override
    public void cancelSubscription(String connectionId, String subscriptionId) {

    }

    @Override
    public Collection<Subscription> getSubscriptions(String connectionId) {
        Subscription s = new Subscription(connectionId, new ArrayList<>());

        return Collections.singletonList(s);
    }

    @Override
    public Subscription getSubscription(String connectionId, String subscriptionId) {
        Subscription s = new Subscription(connectionId, new ArrayList<>());
        return s;
    }

    @Override
    public Map<String, List<String>> getConnectionIdsForResourceIds(List<String> criteria) {
        return Collections.emptyMap();
    }
}
