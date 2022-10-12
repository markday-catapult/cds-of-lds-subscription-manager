package com.catapult.lds.service;

/**
 * {@code UnauthorizedUserException} is an exception that gets thrown when JWT claims validations fails.
 */
public class UnauthorizedUserException extends Exception {

    /**
     * Creates a {@code UnauthorizedUserException} with the given message.
     *
     * @pre message != null
     */
    public UnauthorizedUserException(String message) {
        super(message);

        assert message != null;
    }

}
