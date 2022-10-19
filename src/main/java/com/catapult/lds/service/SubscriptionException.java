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

    /**
     * Creates a {@code SubscriptionException} with the given message and throwable.
     *
     * @pre message != null
     * @pre throwable != null
     */
    public SubscriptionException(String message, Throwable throwable) {
        super(message, throwable);

        assert message != null;
        assert throwable != null;
    }

}
