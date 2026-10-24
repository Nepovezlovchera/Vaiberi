package com.axenix.blueprints.parser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Единая схема "слотов" составных операторов. От неё зависят и построение графа, и правка графа,
 * поэтому ID узлов получаются одинаковыми.
 *
 * ID узла: {@code <methodId>/s<путь>}, например {@code Main.f/s3.t.1}; у метода {@code <methodId>/entry}.
 * Путь: {@code <индекс>(.<слот>.<индекс>)*}, индексы с 1: {@code 3.t.1} — третий оператор метода,
 * в его ветке then первый оператор.
 * Слоты: t/e (if), b (тело цикла, try-блок), c0.. (catch), f (finally), s0.. (записи switch).
 * Оператор с меткой ({@code outer: for ...}) прозрачен: слоты и данные берутся у помеченного оператора.
 * Запись switch со стрелкой и блоком ({@code case 1 -> { ... }}) отдаёт операторы блока.
 */
public final class StmtSlots {
    private StmtSlots() {}

    public static final String ENTRY = "entry";

    public static String entryId(String methodId) {
        return methodId + "/" + ENTRY;
    }

    public static String nodeId(String methodId, String path) {
        return methodId + "/s" + path;
    }

    /** label — подпись ребра в слот (для switch: {@code case 1}, {@code default}); null — по типу ребра. */
    public record Slot(String name, String edgeKind, String label) {
        public Slot(String name, String edgeKind) {
            this(name, edgeKind, null);
        }
    }

    /** Снимает метки: {@code a: b: for (...)} → {@code for (...)}. */
    public static Statement unwrap(Statement s) {
        while (s instanceof LabeledStmt l) s = l.getStatement();
        return s;
    }

    /** Текст записи switch: {@code case 1}, {@code case "A", "B"}, {@code default}. */
    public static String caseLabel(SwitchEntry e) {
        if (e.getLabels().isEmpty()) return "default";
        StringBuilder sb = new StringBuilder(e.isDefault() ? "default, case " : "case ");
        for (int i = 0; i < e.getLabels().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(e.getLabels().get(i));
        }
        return sb.toString();
    }

    /** Расположение оператора: родитель (или null для тела метода), слот, список и индекс в нём. */
    public record Loc(Statement parent, String slot, NodeList<Statement> list, int index, Statement stmt) {}

    public static List<Slot> slots(Statement s) {
        s = unwrap(s);
        List<Slot> out = new ArrayList<>();
        if (s instanceof IfStmt i) {
            out.add(new Slot("t", "true"));
            if (i.getElseStmt().isPresent()) out.add(new Slot("e", "false"));
        } else if (s instanceof ForStmt || s instanceof ForEachStmt || s instanceof WhileStmt || s instanceof DoStmt) {
            out.add(new Slot("b", "body"));
        } else if (s instanceof TryStmt t) {
            out.add(new Slot("b", "body"));
            for (int k = 0; k < t.getCatchClauses().size(); k++) out.add(new Slot("c" + k, "catch"));
            if (t.getFinallyBlock().isPresent()) out.add(new Slot("f", "finally"));
        } else if (s instanceof SwitchStmt sw) {
            for (int k = 0; k < sw.getEntries().size(); k++) out.add(new Slot("s" + k, "body", caseLabel(sw.getEntries().get(k))));
        }
        return out;
    }

    /** Чтение без изменения AST (тело без фигурных скобок отдаётся как список из одного оператора). */
    public static List<Statement> read(Statement s, String slot) {
        s = unwrap(s);
        Statement single = singleBody(s, slot);
        if (single != null) {
            return single instanceof BlockStmt b ? new ArrayList<>(b.getStatements()) : Collections.singletonList(single);
        }
        return new ArrayList<>(write(s, slot, false));
    }

    /**
     * Список для изменения. Если тело было одним оператором без скобок, оно оборачивается в блок
     * (поведение программы не меняется).
     */
    public static NodeList<Statement> write(Statement s, String slot, boolean wrap) {
        s = unwrap(s);
        if (s instanceof TryStmt t) {
            if (slot.equals("b")) return t.getTryBlock().getStatements();
            if (slot.equals("f")) return t.getFinallyBlock().orElseThrow(() -> bad(slot)).getStatements();
            if (slot.startsWith("c")) return t.getCatchClauses().get(index(slot)).getBody().getStatements();
        }
        if (s instanceof SwitchStmt sw && slot.startsWith("s")) {
            int k = index(slot);
            if (k >= sw.getEntries().size()) throw bad(slot);
            SwitchEntry e = sw.getEntries().get(k);
            NodeList<Statement> st = e.getStatements();
            if (e.getType() == SwitchEntry.Type.BLOCK && st.size() == 1 && st.get(0) instanceof BlockStmt b) {
                return b.getStatements();
            }
            if (wrap && (e.getType() == SwitchEntry.Type.EXPRESSION || e.getType() == SwitchEntry.Type.THROWS_STATEMENT)) {
                // `case 1 -> a();` → `case 1 -> { a(); }`, чтобы в запись можно было добавить операторы
                BlockStmt block = new BlockStmt();
                for (Statement x : st) block.addStatement(x.clone());
                e.setType(SwitchEntry.Type.BLOCK);
                st.clear();
                st.add(block);
                return block.getStatements();
            }
            return st;
        }
        Statement body = singleBody(s, slot);
        if (body == null) throw bad(slot);
        if (body instanceof BlockStmt b) return b.getStatements();
        if (!wrap) throw new IllegalStateException("Оператор без блока: используйте read() или wrap=true");
        BlockStmt block = new BlockStmt();
        block.addStatement(body.clone());
        setSingleBody(s, slot, block);
        return block.getStatements();
    }

    private static Statement singleBody(Statement s, String slot) {
        if (s instanceof IfStmt i) {
            if (slot.equals("t")) return i.getThenStmt();
            if (slot.equals("e")) return i.getElseStmt().orElse(null);
        } else if (s instanceof ForStmt f && slot.equals("b")) return f.getBody();
        else if (s instanceof ForEachStmt f && slot.equals("b")) return f.getBody();
        else if (s instanceof WhileStmt w && slot.equals("b")) return w.getBody();
        else if (s instanceof DoStmt d && slot.equals("b")) return d.getBody();
        return null;
    }

    private static void setSingleBody(Statement s, String slot, BlockStmt block) {
        if (s instanceof IfStmt i) {
            if (slot.equals("t")) i.setThenStmt(block); else i.setElseStmt(block);
        } else if (s instanceof ForStmt f) f.setBody(block);
        else if (s instanceof ForEachStmt f) f.setBody(block);
        else if (s instanceof WhileStmt w) w.setBody(block);
        else if (s instanceof DoStmt d) d.setBody(block);
    }

    private static int index(String slot) {
        try {
            return Integer.parseInt(slot.substring(1));
        } catch (NumberFormatException e) {
            throw bad(slot);
        }
    }

    private static IllegalArgumentException bad(String slot) {
        return new IllegalArgumentException("Нет такого слота у оператора: " + slot);
    }

    /** Находит оператор по пути (см. описание класса). Составные тела без скобок оборачиваются в блок. */
    public static Loc locate(BlockStmt body, String path) {
        if (body == null) throw new IllegalArgumentException("У метода нет тела");
        String[] tok = path.split("\\.");
        NodeList<Statement> list = body.getStatements();
        Statement parent = null;
        String slot = null;
        int i = 0;
        while (true) {
            int idx;
            try {
                idx = Integer.parseInt(tok[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Неверный путь узла: " + path);
            }
            idx--; // в ID индексы с 1
            if (idx < 0 || idx >= list.size()) throw new IllegalArgumentException("Узел не найден (индекс " + (idx + 1) + "): " + path);
            Statement st = list.get(idx);
            if (i == tok.length - 1) return new Loc(parent, slot, list, idx, st);
            if (i + 1 >= tok.length) throw new IllegalArgumentException("Неверный путь узла: " + path);
            slot = tok[i + 1];
            parent = st;
            list = write(st, slot, true);
            i += 2;
            if (i >= tok.length) throw new IllegalArgumentException("Неверный путь узла: " + path);
        }
    }

    /** Первый слот-контейнер оператора или null, если оператор не составной. */
    public static String defaultSlot(Statement s) {
        List<Slot> l = slots(s);
        return l.isEmpty() ? null : l.get(0).name();
    }
}
