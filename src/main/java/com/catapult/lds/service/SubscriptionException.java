package com.catapult.lds.service;

/**
 * {@code SubscriptionException} is an exception that gets thrown during subscription operations.
 */
public class SubscriptionException extends Exception {

    /**
     * Creates a {@code SubscriptionException} with the given message.
     *
     * @pre message != null
     */
    public SubscriptionException(String message) {
        super(message);

        assert message != null;
    }

}
