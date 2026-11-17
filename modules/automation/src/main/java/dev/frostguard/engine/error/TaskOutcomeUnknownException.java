package dev.frostguard.engine.error;

/**
 * Signals that an action may have been processed but its resulting game state
 * could not be confirmed. The task should set a bounded retry before throwing.
 */
public class TaskOutcomeUnknownException extends RuntimeException {

    public TaskOutcomeUnknownException(String message) {
        super(message);
    }
}
