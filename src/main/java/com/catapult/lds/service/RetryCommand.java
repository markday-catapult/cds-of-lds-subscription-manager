package com.catapult.lds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 *{@code RetryCommand} provides ability to retry function execution.
 */
public class RetryCommand <T>{

    private final Logger logger = LoggerFactory.getLogger(RetryCommand.class);

    private int retryCounter;
    private final int maxRetries;

    public RetryCommand(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Takes a function and executes it, if fails, passes the function to the retry command
     */
    public T run(Supplier<T> function) {
        try {
            return function.get();
        } catch (Exception e) {
            return retry(function);
        }
    }

    private T retry(Supplier<T> function) throws RuntimeException {
        logger.debug("FAILED - Command failed, will be retried " + maxRetries + " times.");
        retryCounter = 0;
        while (retryCounter < maxRetries) {
            try {
                return function.get();
            } catch (Exception ex) {
                retryCounter++;
                logger.error("Command failed on retry " + retryCounter + " of " + maxRetries + " error: " + ex );
                if (retryCounter >= maxRetries) {
                    logger.debug("Max retries exceeded.");
                    break;
                }
            }
        }
        throw new RuntimeException("Command failed on all of " + maxRetries + " retries");
    }
}
