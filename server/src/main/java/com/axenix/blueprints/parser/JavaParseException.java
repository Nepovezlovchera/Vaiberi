package com.axenix.blueprints.parser;

/** Ошибка разбора пользовательского Java. Контроллер отдаёт её как HTTP 422 со строкой. */
public class JavaParseException extends RuntimeException {
    private final Integer line;

    public JavaParseException(String message, Integer line) {
        super(message);
        this.line = line;
    }

    public Integer line() {
        return line;
    }
}
