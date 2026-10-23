package com.axenix.blueprints.parser;

/** Правка графа привела к коду, который не компилируется. Контроллер отдаёт HTTP 400 с кодом COMPILE_ERROR. */
public class CompileErrorException extends IllegalArgumentException {
    public CompileErrorException(String message) {
        super(message);
    }
}
