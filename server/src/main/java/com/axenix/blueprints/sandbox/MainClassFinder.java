package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.ProjectFile;
import com.github.javaparser.StaticJavaParser;
import com.axenix.blueprints.parser.ProjectModel;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.List;
import java.util.ArrayList;

/** Определяет класс с main, если клиент его не указал. */
public final class MainClassFinder {
    private MainClassFinder() {}

    public static String find(List<ProjectFile> files, String requested) {
        if (requested != null && !requested.isBlank()) return requested.trim();
        for (ProjectFile f : files) {
            try {
                var unit = ProjectModel.parseUnit(ProjectModel.newParser(), f.path(), f.source());
                for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
                    for (MethodDeclaration method : type.getMethodsByName("main")) {
                        if (!method.isStatic() || !method.getType().isVoidType() || method.getParameters().size() != 1)
                            continue;
                        if (!method.isPublic()) continue;
                        String parameter = method.getParameter(0).getType().toString();
                        if (!(parameter.equals("String[]") || parameter.equals("java.lang.String[]")
                                || ((parameter.equals("String") || parameter.equals("java.lang.String"))
                                && method.getParameter(0).isVarArgs()))) continue;
                        ArrayList<String> names = new ArrayList<>();
                        TypeDeclaration<?> current = type;
                        while (current != null) {
                            names.add(0, current.getNameAsString());
                            current = current.findAncestor(TypeDeclaration.class).orElse(null);
                        }
                        String className = String.join("$", names);
                        return unit.getPackageDeclaration().map(p -> p.getNameAsString() + "." + className)
                                .orElse(className);
                    }
                }
            } catch (RuntimeException ignored) {
                // The normal parser endpoint reports syntax errors; continue looking for a main in other files.
            }
        }
        throw new IllegalArgumentException("Не найден класс с методом main(String[])");
    }
}
