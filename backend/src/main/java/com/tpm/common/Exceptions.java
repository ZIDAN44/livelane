package com.tpm.common;

/** Domain exceptions; {@link GlobalExceptionHandler} maps each to an HTTP status. */
public final class Exceptions {

    private Exceptions() {}

    /** HTTP 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String entity, Object id) {
            super("%s %s not found".formatted(entity, id));
        }
    }

    /** Business-rule conflict (duplicate key, stale version, non-empty column...). HTTP 409. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** HTTP 400. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
