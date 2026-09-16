package com.example.urlshortener.exception;

import com.example.urlshortener.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidUrl(
            InvalidUrlException exception) {

        return ResponseEntity
                .status(BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_URL",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity
                .status(BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_REQUEST",
                        message
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception) {

        return ResponseEntity
                .status(BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_REQUEST",
                        "Request body contains invalid or malformed data"
                ));
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleShortUrlNotFound(
            ShortUrlNotFoundException exception) {

        return ResponseEntity
                .status(NOT_FOUND)
                .body(new ApiErrorResponse(
                        "SHORT_URL_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(ShortUrlExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleShortUrlExpired(
            ShortUrlExpiredException exception) {

        return ResponseEntity
                .status(NOT_FOUND)
                .body(new ApiErrorResponse(
                        "SHORT_URL_EXPIRED",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleShortCodeGeneration(
            ShortCodeGenerationException exception) {

        log.error(
                "SHORT_CODE_GENERATION_FAILURE",
                exception
        );

        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "Unable to generate a short URL"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception) {

        log.error(
                "UNEXPECTED_ERROR",
                exception
        );

        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred"
                ));
    }
}