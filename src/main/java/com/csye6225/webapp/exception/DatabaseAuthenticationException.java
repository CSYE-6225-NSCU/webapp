package com.csye6225.webapp.exception;

import org.springframework.security.core.AuthenticationException;

public class DatabaseAuthenticationException extends AuthenticationException {
    public DatabaseAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
