package com.axenix.blueprints.parser;

import com.axenix.blueprints.model.Dto.GraphEdge;
import com.axenix.blueprints.model.Dto.GraphNode;
import com.axenix.blueprints.model.Dto.MethodGraphResponse;
import com.axenix.blueprints.model.Dto.NodeData;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.List;

/**
 * AST метода → граф. Один узел на оператор, выражения остаются текстом.
 * Неизвестные операторы (лямбды, synchronized, вложенные классы) превращаются в узел raw.
 * Метки прозрачны: {@code outer: for ...} — узел loop с подписью, включающей метку.
 */
public final class GraphBuilder {
    private final ProjectModel.MethodRef m;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    private GraphBuilder(ProjectModel.MethodRef m) {
        this.m = m;
    }

    public static MethodGraphResponse build(ProjectModel.MethodRef m) {
        return new GraphBuilder(m).run();
    }

    /** Строки и колонки узла (с 1) для подсветки в редакторе. */
    public record Pos(int lineStart, int lineEnd, int colStart, int colEnd) {}

    private MethodGraphResponse run() {
        Node d = m.decl();
        String sig = m.signature();
        Pos p = pos(d);
        String entry = StmtSlots.entryId(m.id());
        nodes.add(new GraphNode(entry, null, "method",
                new NodeData(sig, sig, p.lineStart(), p.lineEnd(), p.colStart(), p.colEnd(), false, null, List.of(), null, null, null)));
        BlockStmt body = m.body();
        if (body != null) walk(new ArrayList<>(body.getStatements()), entry, "next", null, "");
        return new MethodGraphResponse(m.className(), m.id(), sig, m.file(), p.lineStart(), p.lineEnd(), nodes, edges);
    }

    private void walk(List<Statement> list, String parentId, String firstKind, String firstLabel, String prefix) {
        String prev = null;
        Statement prevStmt = null;
        for (int i = 0; i < list.size(); i++) {
            Statement st = list.get(i);
            String path = prefix + (i + 1);
            String id = StmtSlots.nodeId(m.id(), path);
            nodes.add(new GraphNode(id, parentId, type(st), data(st)));
            if (prev == null) edge(parentId, id, firstKind, firstLabel, parentIsLoop(parentId));
            else edge(prev, id, siblingKind(prevStmt), null, false);
            prev = id;
            prevStmt = st;
            for (StmtSlots.Slot slot : StmtSlots.slots(st)) {
                List<Statement> body = StmtSlots.read(st, slot.name());
                walk(body, id, slot.edgeKind(), slot.label(), path + "." + slot.name() + ".");
                if (type(st).equals("loop") && slot.name().equals("b") && !body.isEmpty()) {
                    // замыкающее ребро: конец тела → заголовок цикла (следующая итерация)
                    String last = StmtSlots.nodeId(m.id(), path + ".b." + body.size());
                    edges.add(new GraphEdge("e_" + shortId(last) + "_" + shortId(id) + "_back", last, id, "back", "Повтор"));
                }
            }
        }
    }

    /** if без else: переход к следующему оператору — это ветка «Нет»; в остальных случаях обычный переход. */
    private static String siblingKind(Statement prevStmt) {
        return StmtSlots.unwrap(prevStmt) instanceof IfStmt i && i.getElseStmt().isEmpty() ? "false" : "next";
    }

    private boolean parentIsLoop(String parentId) {
        for (GraphNode n : nodes) if (n.id().equals(parentId)) return n.type().equals("loop");
        return false;
    }

    private void edge(String from, String to, String kind, String explicitLabel, boolean fromLoop) {
        String label = explicitLabel != null ? explicitLabel : switch (kind) {
            case "true" -> "Да";
            case "false" -> "Нет";
            case "body" -> fromLoop ? "Тело цикла" : "body";
            case "catch" -> "catch";
            case "finally" -> "finally";
            default -> null;
        };
        edges.add(new GraphEdge("e_" + shortId(from) + "_" + shortId(to), from, to, kind, label));
    }

    private static String shortId(String id) {
        return id.substring(id.lastIndexOf('/') + 1);
    }

    private static NodeData data(Statement original) {
        Pos p = pos(original);
        String header = StmtSlots.slots(original).isEmpty() ? null : label(original);
        String loopType = null;
        String condition = null;
        Statement s = StmtSlots.unwrap(original);
        if (s instanceof ForStmt f) {
            loopType = "for";
            condition = f.getCompare().map(Node::toString).orElse(null);
        } else if (s instanceof ForEachStmt f) {
            loopType = "foreach";
            condition = f.getIterable().toString();
        } else if (s instanceof WhileStmt w) {
            loopType = "while";
            condition = w.getCondition().toString();
        } else if (s instanceof DoStmt d) {
            loopType = "do";
            condition = d.getCondition().toString();
        } else if (s instanceof IfStmt i) {
            condition = i.getCondition().toString();
        } else if (s instanceof SwitchStmt sw) {
            condition = sw.getSelector().toString();
        }
        return new NodeData(label(original), original.toString(), p.lineStart(), p.lineEnd(), p.colStart(), p.colEnd(),
                false, null, List.of(), loopType, condition, header);
    }

    public static String type(Statement s) {
        s = StmtSlots.unwrap(s);
        if (s instanceof ExpressionStmt es) {
            if (es.getExpression() instanceof VariableDeclarationExpr) return "var";
            if (es.getExpression() instanceof MethodCallExpr) return "call";
            return "op";
        }
        if (s instanceof ReturnStmt) return "return";
        if (s instanceof IfStmt) return "condition";
        if (s instanceof ForStmt || s instanceof ForEachStmt || s instanceof WhileStmt || s instanceof DoStmt) return "loop";
        if (s instanceof TryStmt) return "try";
        if (s instanceof SwitchStmt) return "switch";
        if (s instanceof BreakStmt) return "break";
        if (s instanceof ContinueStmt) return "continue";
        if (s instanceof ThrowStmt) return "throw";
        return "raw";
    }

    static String label(Statement s) {
        String first = "";
        for (String line : s.toString().split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")) continue;
            first = t;
            break;
        }
        if (first.endsWith("{")) first = first.substring(0, first.length() - 1).trim();
        return first.length() > 120 ? first.substring(0, 117) + "..." : first;
    }

    public static Pos pos(Node n) {
        return n.getRange().map(r -> new Pos(r.begin.line, r.end.line, r.begin.column, r.end.column))
                .orElse(new Pos(0, 0, 0, 0));
    }
}
