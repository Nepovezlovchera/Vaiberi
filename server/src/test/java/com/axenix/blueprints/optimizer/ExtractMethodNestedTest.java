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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Extract Method во вложенных блоках: весь метод — try { while (true) { ... } } (типичный консольный цикл). */
class ExtractMethodNestedTest {

    private static final String LOOP = String.join("\n",
            "import java.util.Scanner;",
            "public class Main {",
            "    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }",
            "    static void session(Scanner in) {",
            "        try {",
            "            while (true) {",
            "                System.out.print(\"a: \");",
            "                double a = Double.parseDouble(in.nextLine());",
            "                System.out.print(\"op: \");",
            "                String op = in.nextLine().trim();",
            "                System.out.print(\"b: \");",
            "                double b = Double.parseDouble(in.nextLine());",
            "                if (op.equals(\"/\") && b == 0) {",
            "                    System.out.println(\"div by zero\");",
            "                } else if (op.equals(\"+\")) {",
            "                    System.out.println(fmt(a + b));",
            "                } else if (op.equals(\"-\")) {",
            "                    System.out.println(fmt(a - b));",
            "                } else {",
            "                    System.out.println(\"op?\");",
            "                }",
            "                System.out.print(\"again? \");",
            "                String again = in.nextLine().trim();",
            "                if (!again.equals(\"y\")) {",
            "                    break;",
            "                }",
            "            }",
            "        } catch (RuntimeException e) {",
            "            System.out.println(\"end\");",
            "        }",
            "    }",
            "    public static void main(String[] args) {",
            "        session(new Scanner(System.in));",
            "    }",
            "}");

    private static String extract(String src, int line) {
        CompilationUnit cu = StaticJavaParser.parse(src);
        LexicalPreservingPrinter.setup(cu);
        Optional<String> r = ExtractMethod.apply(cu, line);
        assertTrue(r.isPresent(), "ожидали выделение метода");
        return LexicalPreservingPrinter.print(cu);
    }

    private static RunResponse run(String src, String stdin) {
        RunServiceImpl run = new RunServiceImpl(new CompilerService(), new ProcessRunner(3000, "128m"));
        return run.run(new RunRequest(List.of(new ProjectFile("Main.java", src)), "Main", stdin));
    }

    @Test
    void methodThatIsOneBigLoopIsSplitInsideTheLoopBody() {
        String after = extract(LOOP, 4);
        assertTrue(after.contains("sessionPart1("), after);
        // цикл остался в исходном методе, а его тело частично вынесено
        assertTrue(after.indexOf("while (true)") < after.indexOf("sessionPart1(in)"), after);
        String in = "6\n+\n4\ny\n5\n/\n0\ny\n1\n*\n2\nn\n";
        RunResponse before = run(LOOP, in);
        RunResponse res = run(after, in);
        assertTrue(res.compiled(), res.compileOutput() + after);
        assertEquals(before.stdout(), res.stdout(), after);
        assertEquals(before.exitCode(), res.exitCode());
    }

    private static final String ACC = String.join("\n",
            "import java.util.Scanner;",
            "public class Main {",
            "    static void report(int total, int min, int max) { System.out.println(total + \" \" + min + \" \" + max); }",
            "    static void stats(int[] xs) {",
            "        int total = 0;",
            "        int min = Integer.MAX_VALUE;",
            "        int max = Integer.MIN_VALUE;",
            "        int evens = 0;",
            "        int odds = 0;",
            "        for (int x : xs) {",
            "            total += x;",
            "            if (x < min) {",
            "                min = x;",
            "            }",
            "            if (x > max) {",
            "                max = x;",
            "            }",
            "            if (x % 2 == 0) {",
            "                evens++;",
            "            } else {",
            "                odds++;",
            "            }",
            "            if (x > 100) {",
            "                System.out.println(\"big \" + x);",
            "            }",
            "        }",
            "        report(total, min, max);",
            "        if (evens > 100) {",
            "            return;",
            "        }",
            "        System.out.println(evens + \"/\" + odds);",
            "    }",
            "    public static void main(String[] args) {",
            "        Scanner in = new Scanner(System.in);",
            "        int n = Integer.parseInt(in.nextLine().trim());",
            "        int[] xs = new int[n];",
            "        for (int i = 0; i < n; i++) xs[i] = Integer.parseInt(in.nextLine().trim());",
            "        stats(xs);",
            "    }",
            "}");

    /** Несколько изменяемых накопителей в цикле: значения возвращаются через маленький класс-результат. */
    @Test
    void severalAccumulatorsAreReturnedThroughResultClass() {
        String after = extract(ACC, 4);
        assertTrue(after.contains("Result"), after);
        String in = "6\n5\n-3\n120\n8\n8\n1\n";
        RunResponse before = run(ACC, in);
        RunResponse res = run(after, in);
        assertTrue(res.compiled(), res.compileOutput() + after);
        assertEquals(before.stdout(), res.stdout(), after);
        assertEquals(0, res.exitCode());
    }
}
