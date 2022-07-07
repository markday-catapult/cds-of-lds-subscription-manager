package com.catapult.lds.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimpleCacheService implements SubscriptionCacheService {

    public static final SubscriptionCacheService instance = new SimpleCacheService();

    @Override
    public void createConnection(String connectionId) throws SubscriptionException {
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
        Subscription s = new Subscription(connectionId);

        return Collections.singletonList(s);
    }

    @Override
    public Subscription getSubscription(String connectionId, String subscriptionId) {
        Subscription s = new Subscription(connectionId);
        return s;
    }

    @Override
    public Map<String, List<String>> getConnectionIdsForResourceIds(List<String> criteria) {
        return Collections.emptyMap();
    }
}
