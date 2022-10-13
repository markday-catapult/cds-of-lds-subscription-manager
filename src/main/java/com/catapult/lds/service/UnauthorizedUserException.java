package com.catapult.lds.service;

/**
 * {@code UnauthorizedUserException} is an exception that gets thrown when a subscription 
 * request is denied due to the requester being unauthorized to access the requested data.
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
