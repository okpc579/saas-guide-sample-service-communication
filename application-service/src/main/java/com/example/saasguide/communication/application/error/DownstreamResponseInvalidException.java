package com.example.saasguide.communication.application.error;

public class DownstreamResponseInvalidException extends RuntimeException {
    public DownstreamResponseInvalidException() {
        super("Invalid downstream response");
    }
}
