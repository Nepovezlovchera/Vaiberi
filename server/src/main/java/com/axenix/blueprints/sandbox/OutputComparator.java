package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.CompareMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Нормализация и сравнение вывода программы до/после (I/O-оракул). */
public final class OutputComparator {
    private OutputComparator() {}

    // Границы слов задаются явно: \\b в новых JDK не считает кириллицу буквами
    private static final String L = "(?<![\\p{L}\\p{N}])";
    private static final String R = "(?![\\p{L}\\p{N}])";
    private static final Pattern DURATION = Pattern.compile(
            L + "\\d+(?:[.,]\\d+)?\\s?(?:мс|ms|сек\\.?|секунд\\p{L}*|sec|seconds|нс|ns|s)" + R,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CLOCK = Pattern.compile(L + "\\d{1,2}:\\d{2}:\\d{2}(?:[.,]\\d+)?" + R);
    private static final Pattern IDENTITY_HASH = Pattern.compile("@[0-9a-f]{5,}");

    public static String normalize(String text, CompareMode mode) {
        String s = text == null ? "" : text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        if (mode == CompareMode.MASKED) {
            s = DURATION.matcher(s).replaceAll("<T>");
            s = CLOCK.matcher(s).replaceAll("<CLOCK>");
            s = IDENTITY_HASH.matcher(s).replaceAll("@<H>");
        }
        if (mode == CompareMode.SORTED_LINES) {
            String[] lines = s.split("\n", -1);
            Arrays.sort(lines);
            s = String.join("\n", lines);
        }
        return s.stripTrailing();
    }

    /** Из stderr убираются строки стека (номера строк меняются при рефакторинге), остаётся тип и сообщение исключения. */
    public static String stripStackTrace(String stderr) {
        if (stderr == null) return "";
        List<String> keep = new ArrayList<>();
        for (String line : stderr.replace("\r\n", "\n").split("\n")) {
            String t = line.stripLeading();
            if (t.startsWith("at ") || t.startsWith("... ")) continue;
            keep.add(line);
        }
        return String.join("\n", keep);
    }

    /** null — равны; иначе человекочитаемое описание первого расхождения. */
    public static String diff(String expected, String actual) {
        if (expected.equals(actual)) return null;
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        int n = Math.max(e.length, a.length);
        for (int i = 0; i < n; i++) {
            String x = i < e.length ? e[i] : "<нет строки>";
            String y = i < a.length ? a[i] : "<нет строки>";
            if (!x.equals(y)) return "строка " + (i + 1) + ": ожидалось «" + cut(x) + "», получено «" + cut(y) + "»";
        }
        return "выводы различаются";
    }

    private static String cut(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
