package dev.herald.api.web;

public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(int limitBytes) {
        super("Event payload exceeds the " + limitBytes + "-byte limit");
    }
}
