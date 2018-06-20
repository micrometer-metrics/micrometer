package io.micrometer.jersey2.server.exception;

public class ResourceGoneException extends RuntimeException {

    public ResourceGoneException() {
        super();
    }

    public ResourceGoneException(String message) {
        super(message);
    }

    public ResourceGoneException(String message, Throwable cause) {
        super(message, cause);
    }
}
