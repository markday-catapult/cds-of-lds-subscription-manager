package com.catapult.lds.service;

/**
 * {@code ResourceNameSpace} enumerates possible resource types that may be subscribed to.  The {@linkplain #value value} of each is intended to be used for cache key name-spacing.
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
