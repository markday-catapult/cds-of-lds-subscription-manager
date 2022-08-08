package com.catapult.lds.service;

import com.catapult.lds.SubscribeRequestHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * {@code SubscriptionRequestResources} contains information about the resources requested in a {@link SubscribeRequestHandler.SubscriptionRequest}
 **/
@Data
@Jacksonized
public class SubscriptionRequestResources {

    public static final String NAMESPACED_RESOURCE_PATTERN = "%s:%s:%s";

    private static final String DEVICE_NAMESPACE_KEY = "device";
    private static final String ATHLETE_NAMESPACE_KEY = "athlete";
    private static final String USER_NAMESPACE_KEY = "user";
    
    @JsonProperty("athleteId")
    private Set<String> athleteIds;
    @JsonProperty("deviceId")
    private Set<String> deviceIds;
    @JsonProperty("userId")
    private Set<String> userIds;

    @JsonIgnore
    public Set<String> getNameSpacedSubscriptionResources(String dataClass){
        Set<String> resources = new HashSet<>();
        if(Objects.nonNull(athleteIds)){
            resources.addAll(athleteIds.stream().map(a-> String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                    dataClass,
                    ATHLETE_NAMESPACE_KEY,
                    a)).collect(toSet()));
        }
        if(Objects.nonNull(deviceIds)){
            resources.addAll(deviceIds.stream().map(d-> String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                    dataClass,
                    DEVICE_NAMESPACE_KEY,
                    d)).collect(toSet()));
        }
        if(Objects.nonNull(userIds)){
            resources.addAll(userIds.stream().map(u-> String.format(SubscribeRequestHandler.NAMESPACED_RESOURCE_PATTERN,
                    dataClass,
                    USER_NAMESPACE_KEY,
                    u)).collect(toSet()));
        }
        return  resources;
    }

}
