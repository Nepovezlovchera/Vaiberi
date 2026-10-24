package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.RunRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Интерактивная консоль: программа останавливается на вводе, получает его по частям. */
class InteractiveRunServiceTest {

    private static final String GREETER = """
            import java.util.Scanner;
            public class Main {
                public static void main(String[] args) {
                    Scanner sc = new Scanner(System.in);
                    System.out.print("Как вас зовут? ");
                    String name = sc.nextLine();
                    System.out.print("Возраст: ");
                    int age = Integer.parseInt(sc.nextLine().trim());
                    System.out.println("Привет, " + name + "! Через год вам " + (age + 1));
                    System.err.println("done");
                }
            }
            """;

    private final InteractiveRunService svc =
            new InteractiveRunService(new CompilerService(), new ProcessRunner(3000, "128m"), 4000, 20000, 5);

    /** Слушатель: копит вывод и отвечает на приглашения, как это делал бы пользователь. */
    private static final class Dialog implements InteractiveRunService.Listener {
        final StringBuffer out = new StringBuffer();
        final StringBuffer err = new StringBuffer();
        final AtomicReference<String> id = new AtomicReference<>();
        final AtomicReference<String> compileError = new AtomicReference<>();
        volatile int exitCode = Integer.MIN_VALUE;
        volatile String reason;
        final java.util.Map<String, String> answers = new java.util.LinkedHashMap<>();
        InteractiveRunService svc;

        public void onSession(String sid) { id.set(sid); }
        public void onCompileError(String o) { compileError.set(o); }
        public void onOut(String t) {
            out.append(t);
            for (var e : answers.entrySet()) {
                if (out.toString().endsWith(e.getKey())) svc.sendInput(id.get(), e.getValue() + "\n", false);
            }
        }
        public void onErr(String t) { err.append(t); }
        public void onExit(int code, long ms, String r) { exitCode = code; reason = r; }
    }

    private static RunRequest req(String src) {
        return new RunRequest(List.of(new ProjectFile("Main.java", src)), "Main", "");
    }

    @Test
    void promptWithoutNewlineIsDeliveredAndInputIsAnswered() {
        Dialog d = new Dialog();
        d.svc = svc;
        d.answers.put("Как вас зовут? ", "Иван");
        d.answers.put("Возраст: ", "41");
        svc.run(req(GREETER), d);
        assertEquals("Как вас зовут? Возраст: Привет, Иван! Через год вам 42\n", d.out.toString());
        assertEquals("done\n", d.err.toString());
        assertEquals(0, d.exitCode);
        assertNull(d.reason);
    }

    @Test
    void reportsCompileErrors() {
        Dialog d = new Dialog();
        d.svc = svc;
        svc.run(req("public class Main { public static void main(String[] a) { int x = \"s\"; } }"), d);
        assertTrue(d.compileError.get() != null && !d.compileError.get().isBlank());
        assertEquals(Integer.MIN_VALUE, d.exitCode);
    }

    @Test
    void eofEndsReadLoop() throws Exception {
        String src = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] a) {
                        Scanner sc = new Scanner(System.in);
                        int sum = 0;
                        System.out.println("числа через Enter, Ctrl+D — конец");
                        while (sc.hasNextInt()) sum += sc.nextInt();
                        System.out.println("сумма=" + sum);
                    }
                }
                """;
        Dialog d = new Dialog();
        d.svc = svc;
        Thread t = new Thread(() -> svc.run(req(src), d));
        t.start();
        while (!d.out.toString().contains("конец")) Thread.sleep(50);
        svc.sendInput(d.id.get(), "1\n2\n", false);
        svc.sendInput(d.id.get(), "3\n", true);
        t.join(10000);
        assertTrue(d.out.toString().endsWith("сумма=6\n"), d.out.toString());
        assertEquals(0, d.exitCode);
    }

    @Test
    void stopKillsWaitingProgram() throws Exception {
        Dialog d = new Dialog();
        d.svc = svc;
        Thread t = new Thread(() -> svc.run(req(GREETER), d));
        t.start();
        while (!d.out.toString().contains("зовут")) Thread.sleep(50);
        svc.stop(d.id.get());
        t.join(10000);
        assertEquals("stopped", d.reason);
        assertTrue(d.exitCode != 0);
        assertThrows(NoSuchElementException.class, () -> svc.sendInput(d.id.get(), "x\n", false));
    }

    @Test
    void idleProgramIsKilled() {
        InteractiveRunService quick =
                new InteractiveRunService(new CompilerService(), new ProcessRunner(3000, "128m"), 700, 20000, 5);
        Dialog d = new Dialog();
        d.svc = quick;
        quick.run(req(GREETER), d); // никто не отвечает на приглашение
        assertEquals("idle", d.reason);
    }

    @Test
    void outputIsCapped() {
        String src = "public class Main { public static void main(String[] a) { String s = \"x\".repeat(1000);"
                + " for (int i = 0; i < 100000; i++) System.out.println(s); } }";
        Dialog d = new Dialog();
        d.svc = svc;
        svc.run(req(src), d);
        assertEquals("output-limit", d.reason);
        assertTrue(d.out.length() <= 1_000_000);
    }
}
