package dev.herald.api.web;

/** The requested resource does not exist <em>for the calling tenant</em>. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
