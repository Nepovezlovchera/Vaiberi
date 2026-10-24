package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.RunRequest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Интерактивный запуск: программа работает в отдельной JVM, её вывод уходит слушателю по мере появления
 * (в том числе приглашения без перевода строки), а ввод можно подавать в любой момент через {@link #sendInput}.
 * Так консоль «останавливается», когда программа ждёт ввод (Scanner/BufferedReader), как в обычном терминале.
 *
 * Лимиты: простой без вывода и ввода (idle), общее время, размер вывода, число одновременных сессий.
 */
public class InteractiveRunService {

    /** События сессии; вызываются из рабочих потоков. */
    public interface Listener {
        void onSession(String id);
        void onCompileError(String output);
        void onOut(String text);
        void onErr(String text);
        /** reason: null (обычное завершение), "idle", "max-time", "output-limit", "stopped". */
        void onExit(int exitCode, long millis, String reason);
    }

    private static final long MAX_OUTPUT_CHARS = 1_000_000;

    private static final class Session {
        final Process process;
        final OutputStream stdin;
        volatile long lastActivity = System.currentTimeMillis();
        volatile String killReason;

        Session(Process process) {
            this.process = process;
            this.stdin = process.getOutputStream();
        }

        void kill(String reason) {
            if (killReason == null) killReason = reason;
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private final CompilerService compiler;
    private final ProcessRunner runner;
    private final long idleMs;
    private final long maxMs;
    private final int maxSessions;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public InteractiveRunService(CompilerService compiler, ProcessRunner runner, long idleMs, long maxMs, int maxSessions) {
        this.compiler = compiler;
        this.runner = runner;
        this.idleMs = idleMs;
        this.maxMs = maxMs;
        this.maxSessions = maxSessions;
    }

    /** Компилирует и запускает проект; блокирует поток до завершения программы. */
    public void run(RunRequest req, Listener l) {
        if (sessions.size() >= maxSessions) {
            throw new IllegalStateException("Слишком много одновременных запусков, закройте лишние и повторите");
        }
        Path dir = null;
        String id = null;
        try {
            dir = Files.createTempDirectory("bp-irun-");
            String main = MainClassFinder.find(req.files(), req.mainClass());
            CompilerService.CompileResult c = compiler.compile(req.files(), dir);
            if (!c.ok()) {
                l.onCompileError(c.output());
                return;
            }
            long t0 = System.nanoTime();
            Session s = new Session(runner.newProcessBuilder(c.classesDir(), main).start());
            id = UUID.randomUUID().toString();
            sessions.put(id, s);
            l.onSession(id);

            long[] total = {0};
            Thread tOut = pump(s.process, false, s, total, l::onOut);
            Thread tErr = pump(s.process, true, s, total, l::onErr);

            while (!s.process.waitFor(200, TimeUnit.MILLISECONDS)) {
                long now = System.currentTimeMillis();
                if (now - s.lastActivity > idleMs) s.kill("idle");
                else if ((System.nanoTime() - t0) / 1_000_000 > maxMs) s.kill("max-time");
            }
            tOut.join(1500);
            tErr.join(1500);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            l.onExit(s.process.exitValue(), ms, s.killReason);
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка запуска: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запуск прерван", e);
        } finally {
            if (id != null) {
                Session s = sessions.remove(id);
                if (s != null) s.kill("stopped");
            }
            RunServiceImpl.deleteQuietly(dir);
        }
    }

    /** Подаёт текст в stdin программы; eof=true закрывает поток ввода (аналог Ctrl+D). */
    public void sendInput(String id, String text, boolean eof) {
        Session s = sessions.get(id);
        if (s == null) throw new NoSuchElementException("Сессия запуска не найдена или уже завершена");
        synchronized (s) {
            try {
                if (text != null && !text.isEmpty()) {
                    s.stdin.write(text.getBytes(StandardCharsets.UTF_8));
                    s.stdin.flush();
                }
                if (eof) s.stdin.close();
                s.lastActivity = System.currentTimeMillis();
            } catch (IOException e) {
                throw new IllegalStateException("Программа уже завершена и не принимает ввод");
            }
        }
    }

    /** Принудительно останавливает программу (кнопка «Стоп», закрытие потока). Неизвестный id игнорируется. */
    public void stop(String id) {
        Session s = id == null ? null : sessions.get(id);
        if (s != null) s.kill("stopped");
    }

    private Thread pump(Process p, boolean stderr, Session s, long[] total, Consumer<String> sink) {
        Thread t = new Thread(() -> {
            char[] buf = new char[2048];
            try (Reader r = new InputStreamReader(stderr ? p.getErrorStream() : p.getInputStream(), StandardCharsets.UTF_8)) {
                int n;
                while ((n = r.read(buf)) != -1) {
                    String text = new String(buf, 0, n).replace("\r", "").replace("\uFEFF", "");
                    if (text.isEmpty()) continue;
                    s.lastActivity = System.currentTimeMillis();
                    boolean over;
                    synchronized (total) {
                        total[0] += text.length();
                        over = total[0] > MAX_OUTPUT_CHARS;
                    }
                    if (over) {
                        s.kill("output-limit");
                        return;
                    }
                    sink.accept(text);
                }
            } catch (IOException ignored) {
                // поток закрыт при остановке процесса
            } catch (RuntimeException e) {
                // получатель вывода отвалился (клиент закрыл поток): останавливаем программу
                s.kill("stopped");
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
