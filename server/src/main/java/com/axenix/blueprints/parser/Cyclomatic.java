package com.axenix.blueprints.parser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;

/** Цикломатическая сложность по McCabe: 1 + число точек ветвления. */
public final class Cyclomatic {
    private Cyclomatic() {}

    public static int of(Node body) {
        if (body == null) return 1;
        int n = 1;
        n += body.findAll(IfStmt.class).size();
        n += body.findAll(ForStmt.class).size();
        n += body.findAll(ForEachStmt.class).size();
        n += body.findAll(WhileStmt.class).size();
        n += body.findAll(DoStmt.class).size();
        n += body.findAll(CatchClause.class).size();
        n += body.findAll(ConditionalExpr.class).size();
        n += (int) body.findAll(SwitchEntry.class).stream().filter(e -> !e.getLabels().isEmpty()).count();
        n += (int) body.findAll(BinaryExpr.class).stream()
                .filter(b -> b.getOperator() == BinaryExpr.Operator.AND || b.getOperator() == BinaryExpr.Operator.OR)
                .count();
        return n;
    }
}
