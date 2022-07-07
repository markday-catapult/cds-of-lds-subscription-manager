package com.catapult.lds.service;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@code InMemorySubscriptionCacheService} is an in memory implementation of {@code SubscriptionCacheService} that is
 * used for testing.
 */
public class InMemorySubscriptionCacheService implements SubscriptionCacheService {

    public static SubscriptionCacheService instance = new InMemorySubscriptionCacheService();

    private Map<String, Map<String, Subscription>> subscriptionsByConnectionId = new ConcurrentHashMap<>();
    private Map<String, List<String>> connectionsByResourceId = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void createConnection(String connectionId) {
        assert connectionId != null;
        subscriptionsByConnectionId.put(connectionId, new HashMap<>());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeConnection(String connectionId) {
        assert connectionId != null;

        if (!subscriptionsByConnectionId.containsKey(connectionId)) {
            return;
        }

        subscriptionsByConnectionId
                .get(connectionId)
                .values()
                .stream()
                .forEach(subscription -> cancelSubscription(connectionId, subscription.getId())); // cancel from all remaining subscriptions

        subscriptionsByConnectionId.remove(connectionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSubscription(Subscription subscription) {
        assert subscription != null;

        String connectionId = subscription.getConnectionId();
        String subscriptionId = subscription.getId();

        System.out.println("Creating subscription");
        System.out.println("connectionId: " + connectionId);
        System.out.println("subscriptionId: " + subscriptionId);

        Map<String, Subscription> subscriptionById = subscriptionsByConnectionId.get(subscriptionId);

        System.out.println("subscriptionById: " + subscriptionById);

        assert subscriptionById != null;

        subscriptionById.put(subscriptionId, subscription);

        JSONArray resourceIdArray = new JSONArray(subscription.getResourceIdsJson());
        for (int i = 0; i < resourceIdArray.length(); i++) {
            String resourceId = resourceIdArray.getString(i);
            if (!connectionsByResourceId.containsKey(resourceId)) {
                connectionsByResourceId.put(resourceId, new ArrayList<>());
            }
            connectionsByResourceId.get(resourceId).add(connectionId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelSubscription(String connectionId, String subscriptionId) {
        assert connectionId != null;
        assert subscriptionId != null;

        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Subscription> getSubscriptions(String connectionId) {
        assert connectionId != null;

        return Optional.ofNullable(subscriptionsByConnectionId.get(connectionId))
                .map(subscriptionMap -> subscriptionMap.values())
                .orElse(Collections.emptySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Subscription getSubscription(String connectionId, String subscriptionId) {
        assert connectionId != null;
        assert subscriptionId != null;

        return Optional.ofNullable(subscriptionsByConnectionId.get(connectionId))
                .map(subscriptionMap -> subscriptionMap.get(subscriptionId))
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> getConnectionIdsForResourceIds(List<String> resourceIds) {
        return resourceIds.stream().collect(Collectors.toMap(k -> k, k -> connectionsByResourceId.get(k)));
    }
}
