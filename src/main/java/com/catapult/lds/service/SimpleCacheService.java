package com.catapult.lds.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleCacheService implements SubscriptionCacheService {

    public static final SubscriptionCacheService instance = new SimpleCacheService();

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void createConnection(String connectionId) {
    }

    @Override
    public boolean connectionExists(String connectionId) {
        return true;
    }

    @Override
    public void closeConnection(String connectionId) {

    }

    @Override
    public void putSubscription(Subscription subscription) {

    }

    @Override
    public void cancelSubscription(String connectionId, String subscriptionId) {

    }

    @Override
    public Collection<Subscription> getSubscriptions(String connectionId) {
        Subscription s = new Subscription(connectionId, new HashSet<>());

        return Collections.singletonList(s);
    }

    @Override
    public Subscription getSubscription(String connectionId, String subscriptionId) {
        return new Subscription(connectionId, Collections.singleton("resource-id"));
    }

    @Override
    public Map<String, Set<String>> getConnectionIdsForResourceIds(Set<String> resourceIds) {
        return resourceIds.stream().collect(Collectors.toMap(s -> s, s -> Collections.singleton("resource-id")));
    }
}
