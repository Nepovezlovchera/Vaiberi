package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.CodeIssue;
import com.axenix.blueprints.model.Dto.OptimizeRequest;
import com.axenix.blueprints.model.Dto.OptimizeResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.VerificationReport;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.ProcessRunner;
import com.axenix.blueprints.sandbox.VerifyServiceImpl;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizerServiceTest {

    private final AnalyzerServiceImpl analyzer = new AnalyzerServiceImpl();
    private final OptimizerServiceImpl optimizer = new OptimizerServiceImpl(analyzer,
            new LlmGateway(Path.of("target/llm-cache-test"), null, null, null));
    private final VerifyServiceImpl verify = new VerifyServiceImpl(new CompilerService(), new ProcessRunner(5000, "128m"));

    private CodeIssue issue(List<ProjectFile> files, String type, String nodePrefix) {
        AnalysisReport r = analyzer.analyze(files);
        return r.issues().stream().filter(i -> i.type().equals(type) && i.nodeId().startsWith(nodePrefix)).findFirst().orElseThrow();
    }

    /** Главный демо-сценарий: автофикс O(n²) и доказательство сохранения поведения. */
    @Test
    void autofixOfUniqueCustomersPreservesBehavior() {
        List<ProjectFile> files = Demo.project();
        CodeIssue i = issue(files, "COSTLY_CALL_IN_LOOP", "Main.uniqueCustomers");
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        assertEquals("TEMPLATE", r.strategy());
        String code = r.files().get(0).source();
        assertTrue(code.contains("HashSet"), code);
        assertTrue(code.contains("resultSet.add(o.customer)"), code);
        // проблема исчезла
        assertTrue(analyzer.analyze(r.files()).issues().stream()
                .noneMatch(x -> x.type().equals("COSTLY_CALL_IN_LOOP") && x.nodeId().startsWith("Main.uniqueCustomers")));
        VerificationReport v = verify.verify(Demo.verify(r.files()));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput());
    }

    @Test
    void readOnlyListInLoopUsesSetContains() {
        String src = "import java.util.*;\npublic class Main {\n"
                + "  static int count(List<String> a, List<String> b) {\n    int c = 0;\n"
                + "    for (String s : a) {\n      if (b.contains(s)) {\n        c++;\n      }\n    }\n    return c;\n  }\n"
                + "  public static void main(String[] x) { System.out.println(count(List.of(\"a\",\"b\"), List.of(\"b\"))); }\n}\n";
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", src));
        CodeIssue i = issue(files, "COSTLY_CALL_IN_LOOP", "Main.count");
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        String code = r.files().get(0).source();
        assertTrue(code.contains("Set<String> bSet = new HashSet<>(b);"), code);
        assertTrue(code.contains("bSet.contains(s)"), code);
    }

    @Test
    void unsafePatternIsNotAutofixed() {
        // условие с && — вставка set.add изменила бы семантику, поэтому фикс обязан отказаться
        String src = "import java.util.*;\npublic class Main {\n"
                + "  static List<String> f(List<String> a, boolean flag) {\n    List<String> r = new ArrayList<>();\n"
                + "    for (String s : a) {\n      if (!r.contains(s) && flag) {\n        r.add(s);\n      }\n    }\n    return r;\n  }\n}\n";
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", src));
        CodeIssue i = issue(files, "COSTLY_CALL_IN_LOOP", "Main.f");
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), false)));
    }

    @Test
    void nestedLoopHasNoTemplateAndLlmIsOptIn() {
        List<ProjectFile> files = Demo.project();
        CodeIssue i = issue(files, "NESTED_LOOP", "Main.findDuplicates");
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), false)));
        // с allowLlm, но без ключа и кэша — понятная ошибка, а не падение
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), true)));
    }

    @Test
    void llmJsonHelpersRoundTrip() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"```java\\nclass A { String s = \\\"п\\u0440\\\"; }\\n```\"}}]}";
        String text = LlmGateway.extractContent(json);
        assertTrue(text.contains("class A { String s = \"пр\"; }"), text);
        assertEquals("\"a\\\"b\\n\"", LlmGateway.quote("a\"b\n"));
    }

    @Test
    void unknownIssueIsBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(Demo.project(), "NOPE", false)));
    }

    /** Клиент вызывает /api/optimize без issueId: применяются все шаблонные исправления, поведение сохраняется. */
    @Test
    void optimizeWithoutIssueIdFixesEverythingFixable() {
        List<ProjectFile> files = Demo.project();
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, null, false));
        assertEquals("TEMPLATE", r.strategy());
        assertTrue(r.files().get(0).source().contains("HashSet"));
        assertTrue(analyzer.analyze(r.files()).issues().stream()
                .noneMatch(x -> x.type().equals("COSTLY_CALL_IN_LOOP") && x.nodeId().startsWith("Main.uniqueCustomers")));
        assertTrue(verify.verify(Demo.verify(r.files())).behaviorMatched());
    }

    @Test
    void optimizeWithoutIssueIdOnCleanCodeIsNoop() {
        List<ProjectFile> files = List.of(new ProjectFile("A.java", "class A { int f() { return 1; } }"));
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, null, false));
        assertEquals(files, r.files());
    }

    private static final String NESTED = String.join("\n",
            "import java.util.*;",
            "public class Main {",
            "    static class Item { final String id; final int v; Item(String id, int v) { this.id = id; this.v = v; } }",
            "    static int merge(List<Item> xs, List<Item> ys) {",
            "        int total = 0;",
            "        for (Item a : xs) {",
            "            for (Item b : ys) {",
            "                if (a.id.equals(b.id)) {",
            "                    total += a.v * b.v;",
            "                    System.out.println(a.id + \":\" + a.v + \"*\" + b.v);",
            "                }",
            "            }",
            "        }",
            "        return total;",
            "    }",
            "    public static void main(String[] args) {",
            "        List<Item> xs = List.of(new Item(\"a\", 1), new Item(\"b\", 2), new Item(\"a\", 3), new Item(\"z\", 9));",
            "        List<Item> ys = List.of(new Item(\"a\", 10), new Item(\"c\", 5), new Item(\"a\", 20), new Item(\"b\", 7));",
            "        System.out.println(merge(xs, ys));",
            "    }",
            "}", "");

    /** Вложенный foreach с equals по ключу → HashMap<Object, List<B>>; порядок и повторы совпадений сохраняются. */
    @Test
    void nestedForEachBecomesHashMapIndexAndKeepsBehavior() {
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", NESTED));
        CodeIssue i = issue(files, "NESTED_LOOP", "Main.merge");
        assertTrue(i.suggestedCode() != null && i.suggestedCode().contains("computeIfAbsent"), String.valueOf(i.suggestedCode()));
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        String code = r.files().get(0).source();
        assertEquals("TEMPLATE", r.strategy());
        assertTrue(code.contains("computeIfAbsent") && code.contains("HashMap"), code);
        assertTrue(analyzer.analyze(r.files()).issues().stream().noneMatch(x -> x.type().equals("NESTED_LOOP")), code);
        VerificationReport v = verify.verify(new com.axenix.blueprints.model.Dto.VerifyRequest(files, r.files(), "Main",
                List.of(new com.axenix.blueprints.model.Dto.TestCase("t", "", null)),
                com.axenix.blueprints.model.Dto.CompareMode.EXACT));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput() + code);
    }

    @Test
    void nestedLoopIsNotFixedWhenInnerCollectionIsMutated() {
        String src = NESTED.replace("total += a.v * b.v;", "total += a.v * b.v; ys.remove(b);");
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", src));
        CodeIssue i = issue(files, "NESTED_LOOP", "Main.merge");
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), false)));
    }

    private static final String LONG = String.join("\n",
            "import java.util.*;",
            "public class Main {",
            "    static int run(int[] data, String label) {",
            "        int total = 0;",
            "        int count = 0;",
            "        for (int i = 0; i < data.length; i++) {",
            "            if (data[i] % 2 == 0) {",
            "                total += data[i];",
            "            } else {",
            "                total -= 1;",
            "            }",
            "            count++;",
            "        }",
            "        System.out.println(label + \" sum=\" + total + \" n=\" + count);",
            "        StringBuilder sb = new StringBuilder();",
            "        for (int i = 0; i < data.length; i++) {",
            "            sb.append(data[i]);",
            "            if (i + 1 < data.length) {",
            "                sb.append(',');",
            "            }",
            "        }",
            "        String joined = sb.toString();",
            "        System.out.println(joined);",
            "        int checks = 0;",
            "        for (int v : data) {",
            "            if (v > 10) {",
            "                checks++;",
            "            }",
            "        }",
            "        System.out.println(\"big=\" + checks);",
            "        int neg = 0;",
            "        for (int v : data) {",
            "            if (v < 3) {",
            "                neg++;",
            "            }",
            "        }",
            "        System.out.println(\"small=\" + neg);",
            "        return total + joined.length() + checks + neg;",
            "    }",
            "    public static void main(String[] a) {",
            "        System.out.println(run(new int[]{1, 2, 3, 20, 30, 7}, \"x\"));",
            "    }",
            "}");

    /** LONG_METHOD: Extract Method — метод стал короче, код компилируется, вывод тот же (Quality Gate). */
    @Test
    void longMethodIsSplitByExtractMethodAndKeepsBehavior() {
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", LONG));
        CodeIssue i = issue(files, "LONG_METHOD", "Main.run");
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        String code = r.files().get(0).source();
        assertEquals("TEMPLATE", r.strategy());
        assertTrue(code.contains("runPart1("), code);
        VerificationReport v = verify.verify(new com.axenix.blueprints.model.Dto.VerifyRequest(files, r.files(), "Main",
                List.of(new com.axenix.blueprints.model.Dto.TestCase("t", "", null)),
                com.axenix.blueprints.model.Dto.CompareMode.EXACT));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput() + code);
        // у исходного метода строк стало меньше
        int before = LONG.split("\n").length;
        assertTrue(code.split("\n").length > before - 5, code);
    }

    @Test
    void longMethodWithEarlyReturnIsNotExtracted() {
        String src = LONG.replace("        int count = 0;", "        int count = 0;\n        if (data.length == 0) return 0;");
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", src));
        CodeIssue i = issue(files, "LONG_METHOD", "Main.run");
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        // разрешено выделять другие блоки (без return), но результат обязан компилироваться и совпадать по поведению
        VerificationReport v = verify.verify(new com.axenix.blueprints.model.Dto.VerifyRequest(files, r.files(), "Main",
                List.of(new com.axenix.blueprints.model.Dto.TestCase("t", "", null)),
                com.axenix.blueprints.model.Dto.CompareMode.EXACT));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput() + r.files().get(0).source());
    }

    private static final String DUP = String.join("\n",
            "import java.util.*;",
            "public class Main {",
            "    static int first(List<Integer> xs) {",
            "        if (xs == null || xs.isEmpty()) {",
            "            return -1;",
            "        }",
            "        int best = xs.get(0);",
            "        for (int x : xs) {",
            "            if (x > best) best = x;",
            "        }",
            "        return best;",
            "    }",
            "    static int second(List<Integer> ys) {",
            "        if (ys == null || ys.isEmpty()) {",
            "            return -1;",
            "        }",
            "        int top = ys.get(0);",
            "        for (int y : ys) {",
            "            if (y > top) top = y;",
            "        }",
            "        return top;",
            "    }",
            "    static int third(List<Integer> xs) {",
            "        if (xs == null || xs.isEmpty()) {",
            "            return 0;",
            "        }",
            "        return xs.size();",
            "    }",
            "    public static void main(String[] a) {",
            "        System.out.println(first(List.of(3, 9, 4)) + \" \" + second(new ArrayList<>()) + \" \" + third(List.of(1, 2)));",
            "    }",
            "}");

    private void assertSameBehavior(List<ProjectFile> before, List<ProjectFile> after) {
        VerificationReport v = verify.verify(new com.axenix.blueprints.model.Dto.VerifyRequest(before, after, "Main",
                List.of(new com.axenix.blueprints.model.Dto.TestCase("t", "", null)),
                com.axenix.blueprints.model.Dto.CompareMode.EXACT));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput() + after.get(0).source());
    }

    @Test
    void duplicatedConditionBecomesSharedHelper() {
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", DUP));
        CodeIssue i = analyzer.analyze(files).issues().stream()
                .filter(x -> x.type().equals("DUPLICATION") && x.title().equals("Повторяющееся условие")).findFirst().orElseThrow();
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        String code = r.files().get(0).source();
        assertTrue(code.contains("isNullOrEmpty("), code);
        assertTrue(analyzer.analyze(r.files()).issues().stream().noneMatch(x -> x.title().equals("Повторяющееся условие")), code);
        assertSameBehavior(files, r.files());
    }

    @Test
    void duplicatedMethodDelegatesToFirst() {
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", DUP));
        CodeIssue i = analyzer.analyze(files).issues().stream()
                .filter(x -> x.type().equals("DUPLICATION") && x.title().equals("Дублирование логики")).findFirst().orElseThrow();
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        assertTrue(r.files().get(0).source().contains("return first(ys);"), r.files().get(0).source());
        assertSameBehavior(files, r.files());
    }

    private static final String IDX = String.join("\n",
            "import java.util.*;",
            "public class Main {",
            "    static class Order {",
            "        final int id;",
            "        final String name;",
            "        Order(int id, String name) { this.id = id; this.name = name; }",
            "    }",
            "    static int countDuplicateIds(List<Order> orders) {",
            "        int count = 0;",
            "        for (int i = 0; i < orders.size(); i++) {",
            "            for (int j = i + 1; j < orders.size(); j++) {",
            "                if (orders.get(i).id == orders.get(j).id) {",
            "                    count += j - i;",
            "                }",
            "            }",
            "        }",
            "        return count;",
            "    }",
            "    static int byName(List<Order> orders) {",
            "        int count = 0;",
            "        for (int i = 0; i < orders.size(); i++) {",
            "            for (int j = 0; j < orders.size(); j++) {",
            "                if (orders.get(i).name.equals(orders.get(j).name)) {",
            "                    count += i * 3 + j;",
            "                }",
            "            }",
            "        }",
            "        return count;",
            "    }",
            "    static int refCompare(List<Order> orders) {",
            "        int count = 0;",
            "        for (int i = 0; i < orders.size(); i++) {",
            "            for (int j = i + 1; j < orders.size(); j++) {",
            "                if (orders.get(i).name == orders.get(j).name) {",
            "                    count++;",
            "                }",
            "            }",
            "        }",
            "        return count;",
            "    }",
            "    public static void main(String[] a) {",
            "        List<Order> l = new ArrayList<>();",
            "        String[] n = {\"a\", \"b\", \"a\", \"c\", \"b\"};",
            "        for (int k = 0; k < 30; k++) l.add(new Order(k % 7, n[k % 5]));",
            "        System.out.println(countDuplicateIds(l) + \" \" + byName(l) + \" \" + refCompare(l));",
            "    }",
            "}");

    /** Индексные циклы i/j: индекс позиций по ключу; == только для примитивов, у ссылочных типов шаблон отказывает. */
    @Test
    void indexedPairLoopsUsePositionIndexAndKeepBehavior() {
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", IDX));
        OptimizeResponse r1 = optimizer.optimize(new OptimizeRequest(files, issue(files, "NESTED_LOOP", "Main.countDuplicateIds").id(), false));
        List<ProjectFile> step = r1.files();
        assertTrue(step.get(0).source().contains("posByKey"), step.get(0).source());
        OptimizeResponse r2 = optimizer.optimize(new OptimizeRequest(step, issue(step, "NESTED_LOOP", "Main.byName").id(), false));
        assertSameBehavior(files, r2.files());
        assertTrue(analyzer.analyze(r2.files()).issues().stream()
                .noneMatch(x -> x.type().equals("NESTED_LOOP") && x.nodeId().startsWith("Main.countDuplicateIds")), r2.files().get(0).source());
        CodeIssue unsafe = issue(files, "NESTED_LOOP", "Main.refCompare");
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, unsafe.id(), false)));
    }
}
