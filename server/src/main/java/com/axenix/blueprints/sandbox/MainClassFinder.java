package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.ProjectFile;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Определяет класс с main, если клиент его не указал. */
public final class MainClassFinder {
    private MainClassFinder() {}

    private static final Pattern MAIN = Pattern.compile("static\\s+void\\s+main\\s*\\(");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    public static String find(List<ProjectFile> files, String requested) {
        if (requested != null && !requested.isBlank()) return requested.trim();
        for (ProjectFile f : files) {
            if (!MAIN.matcher(f.source()).find()) continue;
            String base = f.path().replace('\\', '/');
            base = base.substring(base.lastIndexOf('/') + 1);
            if (base.endsWith(".java")) base = base.substring(0, base.length() - 5);
            Matcher pkg = PACKAGE.matcher(f.source());
            return pkg.find() ? pkg.group(1) + "." + base : base;
        }
        throw new IllegalArgumentException("Не найден класс с методом main(String[])");
    }
}
