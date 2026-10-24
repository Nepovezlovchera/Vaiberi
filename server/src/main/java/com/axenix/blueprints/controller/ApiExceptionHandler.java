package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.Dto.ErrorResponse;
import com.axenix.blueprints.parser.CompileErrorException;
import com.axenix.blueprints.parser.JavaParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", "Некорректный JSON запроса", null));
    }

    /** Ошибка разбора пользовательского Java: фронт покажет её в Monaco маркером. */
    @ExceptionHandler(JavaParseException.class)
    public ResponseEntity<ErrorResponse> parse(JavaParseException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("PARSE_ERROR", e.getMessage(), e.line()));
    }

    /** Dry run: правка графа даёт некомпилируемый код. Клиент оставляет прежнее состояние. */
    @ExceptionHandler(CompileErrorException.class)
    public ResponseEntity<ErrorResponse> compile(CompileErrorException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("COMPILE_ERROR", e.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", e.getMessage(), null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", e.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> other(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("API request failed", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL", "Внутренняя ошибка сервера", null));
    }

    @ExceptionHandler(java.util.concurrent.RejectedExecutionException.class)
    public ResponseEntity<ErrorResponse> busy(java.util.concurrent.RejectedExecutionException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse("BUSY", "Очередь проверок заполнена", null));
    }
}
