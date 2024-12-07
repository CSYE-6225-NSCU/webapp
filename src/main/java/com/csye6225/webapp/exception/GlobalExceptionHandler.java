package com.csye6225.webapp.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.net.ConnectException;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Handle unsupported HTTP methods (405 Method Not Allowed)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<String> handleUserAlreadyExistsException() {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    // Handles exception when request body improper
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleValidationExceptions() {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    //Handle Database access issues
    @ExceptionHandler(ConnectException.class)
    public ResponseEntity<Map<String, String>> handleConnectException() {
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, String>> handleDataAccessException() {
        return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
