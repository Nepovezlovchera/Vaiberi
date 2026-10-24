package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.RunRequest;
import com.axenix.blueprints.model.Dto.RunResponse;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.ProcessRunner;
import com.axenix.blueprints.sandbox.RunServiceImpl;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Вложенный цикл с условием-конъюнкцией «a != b && a.id.equals(b.id)» заменяется индексом по ключу. */
class TemplateFixesConjunctionTest {

    private static final String SRC = String.join("\n",
            "import java.util.*;",
            "public class Main {",
            "    static class Order { final String id; Order(String id) { this.id = id; } }",
            "    static List<String> duplicateIds(List<Order> orders) {",
            "        List<String> duplicates = new ArrayList<>();",
            "        for (Order a : orders) {",
            "            for (Order b : orders) {",
            "                if (a != b && a.id.equals(b.id)) {",
            "                    if (!duplicates.contains(a.id)) {",
            "                        duplicates.add(a.id);",
            "                    }",
            "                }",
            "            }",
            "        }",
            "        return duplicates;",
            "    }",
            "    public static void main(String[] args) {",
            "        List<Order> os = new ArrayList<>();",
            "        for (String s : new String[]{\"x\", \"y\", \"x\", \"z\", \"y\", \"x\", \"q\"}) os.add(new Order(s));",
            "        System.out.println(duplicateIds(os));",
            "    }",
            "}");

    @Test
    void conjunctionWithIdentityCheckUsesKeyIndexAndKeepsBehavior() {
        CompilationUnit cu = StaticJavaParser.parse(SRC);
        LexicalPreservingPrinter.setup(cu);
        assertTrue(TemplateFixes.nestedLoopToMap(cu, 6).isPresent());
        String after;
        try {
            after = LexicalPreservingPrinter.print(cu);
        } catch (RuntimeException e) {
            after = cu.toString(); // старые версии JavaParser спотыкаются на лямбдах в новых узлах
        }
        assertTrue(after.contains("computeIfAbsent"), after);
        assertTrue(after.contains("a != b"), after); // остаток условия сохранён внутри цикла по совпадениям
        RunServiceImpl run = new RunServiceImpl(new CompilerService(), new ProcessRunner(3000, "128m"));
        RunResponse before = run.run(new RunRequest(List.of(new ProjectFile("Main.java", SRC)), "Main", ""));
        RunResponse res = run.run(new RunRequest(List.of(new ProjectFile("Main.java", after)), "Main", ""));
        assertTrue(res.compiled(), res.compileOutput() + after);
        assertEquals(before.stdout(), res.stdout(), after);
        assertEquals("[x, y]\n", res.stdout());
    }

    @Test
    void conditionWithSideEffectsIsNotTouched() {
        String src = SRC.replace("a != b && a.id.equals(b.id)", "a.id.equals(b.id) && bump()")
                .replace("    static List<String> duplicateIds", "    static boolean bump() { return true; }\n    static List<String> duplicateIds");
        CompilationUnit cu = StaticJavaParser.parse(src);
        LexicalPreservingPrinter.setup(cu);
        assertTrue(TemplateFixes.nestedLoopToMap(cu, 7).isEmpty());
    }
}
