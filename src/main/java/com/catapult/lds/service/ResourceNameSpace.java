package com.catapult.lds.service;

import com.catapult.lds.SubscribeRequestHandler;

/**
 * {@code ResourceNameSpace} namespace mapping
 * for {@link SubscribeRequestHandler.SubscriptionRequest.SubscriptionRequestResources}.
 **/
public enum ResourceNameSpace {

    ATHLETE("athlete"),
    DEVICE("device"),
    USER("user");

    private String value;
    ResourceNameSpace(String value) { this.value = value; }

    public String value(){
        return this.value;
    }

}
