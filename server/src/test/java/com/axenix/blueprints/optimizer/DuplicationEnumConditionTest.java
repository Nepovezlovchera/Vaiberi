package com.axenix.blueprints.optimizer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Повторяющееся условие с константой enum (Type.CONSTANT) выносится в метод. */
class DuplicationEnumConditionTest {

    private static final String SRC = String.join("\n",
            "public class Main {",
            "    enum Kind { A, B }",
            "    static class Item { Kind kind() { return Kind.A; } }",
            "    static int f(Item it) {",
            "        int s = 0;",
            "        if (it.kind() == Kind.A) s++;",
            "        return s;",
            "    }",
            "    static int g(java.util.List<Item> items) {",
            "        int s = 0;",
            "        for (Item it : items) {",
            "            if (it.kind() == Kind.A) {",
            "                s += 2;",
            "            }",
            "        }",
            "        return s;",
            "    }",
            "}");

    @Test
    void enumConstantInConditionIsExtracted() {
        CompilationUnit cu = StaticJavaParser.parse(SRC);
        assertTrue(DuplicationFix.condition(cu, 12).isPresent());
        String out = cu.toString();
        assertTrue(out.contains("private static boolean condition(Item it)"), out);
        assertTrue(out.contains("if (condition(it))"), out);
    }
}
