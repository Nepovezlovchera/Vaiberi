package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.ProjectFile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Компиляция проекта через javax.tools во временной директории (пользовательский код здесь НЕ выполняется). */
public class CompilerService {

    public record CompileResult(boolean ok, String output, Path classesDir) {}

    public CompileResult compile(List<ProjectFile> files, Path workDir) throws IOException {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) throw new IllegalStateException("Нет компилятора: запускайте сервер на JDK, а не на JRE");
        Path src = workDir.resolve("src");
        Path out = workDir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        List<File> toCompile = new ArrayList<>();
        for (ProjectFile f : files) {
            Path target = src.resolve(f.path()).normalize();
            if (f.path().isBlank() || !target.startsWith(src)) {
                throw new IllegalArgumentException("Недопустимый путь файла: " + f.path());
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, f.source(), StandardCharsets.UTF_8);
            toCompile.add(target.toFile());
        }
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm = jc.getStandardFileManager(diags, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> opts = List.of("-encoding", "UTF-8", "-d", out.toString(), "-proc:none", "-Xlint:none");
            ok = jc.getTask(null, fm, diags, opts, null, fm.getJavaFileObjectsFromFiles(toCompile)).call();
        }
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR && ok) continue;
            String name = d.getSource() == null ? "" : Path.of(d.getSource().getName()).getFileName().toString();
            sb.append(d.getKind()).append(' ').append(name).append(':').append(d.getLineNumber()).append(": ")
                    .append(d.getMessage(Locale.ROOT)).append('\n');
        }
        return new CompileResult(ok, sb.toString().trim(), out);
    }
}
