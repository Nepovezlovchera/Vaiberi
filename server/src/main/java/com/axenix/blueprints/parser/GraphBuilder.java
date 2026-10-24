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
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * AST метода → граф. Один узел на оператор, выражения остаются текстом.
 * Неизвестные операторы (лямбды, synchronized, вложенные классы) превращаются в узел raw.
 * Метки прозрачны: {@code outer: for ...} — узел loop с подписью, включающей метку.
 */
public final class GraphBuilder {
    private final ProjectModel.MethodRef m;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Map<Statement, String> statementIds = new IdentityHashMap<>();
    private final Map<Statement, String> afterStatements = new IdentityHashMap<>();

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

    private record Jump(String breakTarget, String continueTarget) {}

    private void walk(List<Statement> list, String parentId, String firstKind, String firstLabel, String prefix) {
        addNodes(list, parentId, prefix);
        if (list.isEmpty()) return;
        edge(parentId, entry(list.get(0), id(prefix, 0)), firstKind, firstLabel, parentIsLoop(parentId));
        connectList(list, prefix, null, new Jump(null, null));
    }

    private String id(String prefix, int index) { return StmtSlots.nodeId(m.id(), prefix + (index + 1)); }

    private void addNodes(List<Statement> list, String parentId, String prefix) {
        for (int i = 0; i < list.size(); i++) {
            Statement st = list.get(i);
            String node = id(prefix, i);
            statementIds.put(st, node);
            statementIds.put(StmtSlots.unwrap(st), node);
            nodes.add(new GraphNode(node, parentId, type(st), data(st)));
            for (StmtSlots.Slot slot : StmtSlots.slots(st))
                addNodes(StmtSlots.read(st, slot.name()), node, prefix + (i + 1) + "." + slot.name() + ".");
        }
    }

    private void connectList(List<Statement> list, String prefix, String continuation, Jump jump) {
        for (int i = 0; i < list.size(); i++) {
            Statement statement = StmtSlots.unwrap(list.get(i));
            String node = id(prefix, i);
            String next = i + 1 < list.size() ? entry(list.get(i + 1), id(prefix, i + 1)) : continuation;
            afterStatements.put(list.get(i), next);
            afterStatements.put(statement, next);
            String path = prefix + (i + 1);
            if (statement instanceof ReturnStmt || statement instanceof ThrowStmt) continue;
            if (statement instanceof BreakStmt br) {
                connect(node, br.getLabel().map(l -> labelledTarget(br, l.asString(), false)).orElse(jump.breakTarget()), "next", "break");
            } else if (statement instanceof ContinueStmt co) {
                connect(node, co.getLabel().map(l -> labelledTarget(co, l.asString(), true)).orElse(jump.continueTarget()), "back", "continue");
            } else if (statement instanceof IfStmt condition) {
                branch(statement, "t", node, path, next, jump, "true", "Да");
                if (condition.getElseStmt().isPresent())
                    branch(statement, "e", node, path, next, jump, "false", "Нет");
                else connect(node, next, "false", "Нет");
            } else if (type(statement).equals("loop")) {
                branch(statement, "b", node, path, node, new Jump(next, node), "body", "Тело цикла");
                connect(node, next, next != null && next.equals(jump.continueTarget()) ? "back" : "false", "Выход");
            } else if (statement instanceof SwitchStmt sw) {
                boolean hasDefault = false;
                for (int k = 0; k < sw.getEntries().size(); k++) {
                    var entry = sw.getEntry(k);
                    hasDefault |= entry.getLabels().isEmpty();
                    String fallthrough = entry.getType() == com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP
                            ? firstCase(sw, k + 1, path, next) : next;
                    branch(statement, "s" + k, node, path, fallthrough,
                            new Jump(next, jump.continueTarget()), "body", StmtSlots.caseLabel(entry));
                }
                if (!hasDefault) connect(node, next, "false", "Нет совпадения");
            } else if (statement instanceof TryStmt attempt) {
                String after = attempt.getFinallyBlock().filter(b -> !b.isEmpty())
                        .map(b -> StmtSlots.nodeId(m.id(), path + ".f.1")).orElse(next);
                branch(statement, "b", node, path, after, jump, "body", "try");
                for (int k = 0; k < attempt.getCatchClauses().size(); k++)
                    branch(statement, "c" + k, node, path, after, jump, "catch", "catch");
                if (attempt.getFinallyBlock().isPresent()) {
                    List<Statement> body = StmtSlots.read(statement, "f");
                    connectList(body, path + ".f.", next, jump);
                }
            } else {
                connect(node, next, next != null && next.equals(jump.continueTarget()) ? "back" : "next", null);
            }
        }
    }

    private String firstCase(SwitchStmt sw, int from, String path, String fallback) {
        for (int k = from; k < sw.getEntries().size(); k++) {
            if (!StmtSlots.read(sw, "s" + k).isEmpty()) return StmtSlots.nodeId(m.id(), path + ".s" + k + ".1");
        }
        return fallback;
    }

    private void branch(Statement statement, String slot, String node, String path, String continuation,
                        Jump jump, String kind, String label) {
        List<Statement> body = StmtSlots.read(statement, slot);
        String prefix = path + "." + slot + ".";
        connect(node, body.isEmpty() ? continuation : entry(body.get(0), id(prefix, 0)), kind, label);
        connectList(body, prefix, continuation, jump);
    }

    private void connect(String from, String to, String kind, String label) {
        if (to != null) edge(from, to, kind, label, parentIsLoop(from));
    }

    private String entry(Statement st, String fallback) {
        Statement unwrapped = StmtSlots.unwrap(st);
        if (unwrapped instanceof DoStmt) {
            List<Statement> body = StmtSlots.read(unwrapped, "b");
            if (!body.isEmpty()) return entry(body.get(0), fallback + ".b.1");
        }
        return fallback;
    }

    private String labelledTarget(Node source, String label, boolean continuing) {
        Node parent = source.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof LabeledStmt marked && marked.getLabel().asString().equals(label))
                return continuing ? statementIds.get(StmtSlots.unwrap(marked)) : afterStatements.get(marked);
            parent = parent.getParentNode().orElse(null);
        }
        return null;
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
        edges.add(new GraphEdge("e_" + shortId(from) + "_" + shortId(to) + "_" + edges.size(), from, to, kind, label));
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
