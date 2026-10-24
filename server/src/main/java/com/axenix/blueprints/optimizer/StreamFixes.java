package com.axenix.blueprints.optimizer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.axenix.blueprints.parser.ProjectModel;
import java.util.Optional;
import java.util.Set;

/** Hoist a read-only membership index for an eager sequential Stream.filter. */
final class StreamFixes {
    private StreamFixes() {}

    static Optional<String> apply(CompilationUnit cu, int line) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("contains") || call.getArguments().size() != 1
                    || call.getBegin().map(p -> p.line).orElse(-1) != line
                    || call.getScope().filter(Expression::isNameExpr).isEmpty()) continue;
            LambdaExpr lambda = call.findAncestor(LambdaExpr.class).orElse(null);
            if (lambda == null || lambda.getParameters().size() != 1) continue;
            String variable = call.getScope().get().toString();
            if (!call.getArgument(0).toString().equals(lambda.getParameter(0).getNameAsString())) continue;
            String expression = lambda.getExpressionBody().map(Object::toString).orElse("");
            if (!expression.equals(call.toString()) && !expression.equals("!" + call)) continue;
            if (!(lambda.getParentNode().orElse(null) instanceof MethodCallExpr filter)
                    || !filter.getNameAsString().equals("filter")
                    || !(filter.getScope().orElse(null) instanceof MethodCallExpr stream)
                    || !stream.getNameAsString().equals("stream")) continue;
            Statement statement = filter.findAncestor(Statement.class).orElse(null);
            if (statement == null || !(statement.getParentNode().orElse(null) instanceof BlockStmt block)) continue;
            // Restrict to one pure callback and an eager terminal: no lazy Stream escaping its scope.
            if (statement.findAll(LambdaExpr.class).size() != 1) continue;
            var calls = statement.findAll(MethodCallExpr.class);
            if (calls.stream().anyMatch(c -> !Set.of("contains", "stream", "filter", "count", "toList").contains(c.getNameAsString()))) continue;
            if (calls.stream().noneMatch(c -> Set.of("count", "toList").contains(c.getNameAsString()))) continue;
            CallableDeclaration<?> method = call.findAncestor(CallableDeclaration.class).orElse(null);
            if (method == null) continue;
            ClassOrInterfaceType type = method.getParameters().stream()
                    .filter(p -> p.getNameAsString().equals(variable) && p.getType() instanceof ClassOrInterfaceType)
                    .map(p -> p.getType().asClassOrInterfaceType()).findFirst().orElse(null);
            if (type == null || !Set.of("List", "ArrayList", "LinkedList").contains(type.getNameAsString())
                    || type.getTypeArguments().isEmpty() || type.getTypeArguments().get().size() != 1) continue;
            String element = type.getTypeArguments().get().get(0).toString();
            if (!Set.of("String", "Integer", "Long", "Short", "Byte", "Character", "Boolean", "Double", "Float").contains(element)) continue;
            String name = variable + "MembershipIndex";
            while (method.toString().contains(name)) name += "_";
            Statement index = ProjectModel.newParser().parseStatement("java.util.Set<" + element + "> " + name
                    + " = new java.util.HashSet<>(" + variable + ");").getResult().orElseThrow();
            call.setScope(new NameExpr(name));
            block.addStatement(block.getStatements().indexOf(statement), index);
            return Optional.of("Stream.filter: List.contains заменён индексом HashSet перед eager terminal; ожидается O(n + m) вместо O(n·m). Проверьте Quality Gate.");
        }
        return Optional.empty();
    }
}
