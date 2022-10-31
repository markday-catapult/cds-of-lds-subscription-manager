package com.catapult.lds.service;

/**
 * {@code InvalidRequestException} is an exception that gets thrown when a client's request is invalid.
 */
public class InvalidRequestException extends Exception {

    /**
     * Creates a {@code InvalidRequestException} with the given message.
     *
     * @pre message != null
     */
    public InvalidRequestException(String message) {
        super(message);

        assert message != null;
    }
}
