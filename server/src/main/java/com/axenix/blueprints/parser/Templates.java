package com.axenix.blueprints.parser;

/** Шаблоны блоков для drag-and-drop из палитры (kind узла → код). */
final class Templates {
    private Templates() {}

    static String code(String template) {
        if (template == null) throw new IllegalArgumentException("Не указан ни code, ни template");
        return switch (template) {
            case "var" -> "int value = 0;";
            case "if" -> "if (condition) {\n}";
            case "loop", "for" -> "for (int i = 0; i < n; i++) {\n}";
            case "foreach" -> "for (var item : items) {\n}";
            case "while" -> "while (condition) {\n}";
            case "try" -> "try {\n} catch (Exception e) {\n}";
            case "switch" -> "switch (value) {\n    default:\n        break;\n}";
            case "op" -> "value = value;";
            case "call" -> "System.out.println();";
            case "return" -> "return;";
            case "break" -> "break;";
            case "continue" -> "continue;";
            case "throw" -> "throw new RuntimeException();";
            default -> throw new IllegalArgumentException("Неизвестный шаблон: " + template);
        };
    }
}
