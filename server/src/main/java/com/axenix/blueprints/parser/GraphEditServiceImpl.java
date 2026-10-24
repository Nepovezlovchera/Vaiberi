package com.axenix.blueprints.parser;

import com.axenix.blueprints.model.Dto.MutateRequest;
import com.axenix.blueprints.model.Dto.MutateResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Services.GraphEditService;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.RunServiceImpl;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Правка графа → правка AST → код (спецификация 4.3).
 * Действия: UPDATE_CODE, DELETE_NODE, INSERT_NODE, REORDER.
 * Ссылка на узел: {@code <methodId>/entry | <methodId>/s<путь>[:<слот>]}. Слот нужен для контейнера
 * (t/e у if, b у цикла, c0/f у try); по умолчанию первый слот.
 * Для REORDER индекс — позиция в целевом списке ПОСЛЕ изъятия узла; перенос только внутри одного метода.
 * Dry run: результат компилируется до ответа; при ошибке — {@link CompileErrorException}, состояние клиента не меняется.
 */
public class GraphEditServiceImpl implements GraphEditService {

    private final ParseServiceImpl parseService;
    private final CompilerService compiler;

    public GraphEditServiceImpl(ParseServiceImpl parseService, CompilerService compiler) {
        this.parseService = parseService;
        this.compiler = compiler;
    }

    @Override
    public MutateResponse mutate(MutateRequest req) {
        ProjectModel pm = ProjectModel.parse(req.files());
        JavaParser parser = ProjectModel.newParser();
        String action = req.action() == null ? "" : req.action().toUpperCase(Locale.ROOT);
        switch (action) {
            case "UPDATE_CODE" -> updateCode(pm, parser, req.nodeId(), req.payload());
            case "DELETE_NODE" -> delete(pm, req.nodeId());
            case "INSERT_NODE" -> insert(pm, parser, req);
            case "INSERT_METHOD" -> insertMethod(pm, parser, req);
            case "REORDER", "MOVE_NODE" -> move(pm, req.nodeId(), req.parentId(), req.index());
            default -> throw new IllegalArgumentException("Неизвестное действие: " + req.action()
                    + " (ожидается UPDATE_CODE, DELETE_NODE, INSERT_NODE, REORDER)");
        }

        List<ProjectFile> files = pm.render();
        // результат обязан снова разбираться
        ProjectModel check;
        try {
            check = ProjectModel.parse(files);
        } catch (JavaParseException e) {
            throw new IllegalArgumentException("Правка приводит к некорректному Java: " + e.getMessage());
        }
        boolean viaTemplate = action.equals("INSERT_NODE") && (req.payload() == null || req.payload().isBlank());
        boolean validate = req.validate() != null ? req.validate() : !viaTemplate;
        boolean validated = false;
        String note = "";
        if (validate) {
            CompilerService.CompileResult r = tryCompile(files);
            if (r.ok()) {
                validated = true;
            } else if (tryCompile(req.files()).ok()) {
                throw new CompileErrorException("Правка приводит к ошибке компиляции:\n" + r.output());
            } else {
                note = "Проект не компилировался и до правки, проверка пропущена:\n" + r.output();
            }
        }
        return new MutateResponse(files, parseService.graph(check, null), validated, note);
    }

    private CompilerService.CompileResult tryCompile(List<ProjectFile> files) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("bp-mut-");
            return compiler.compile(files, dir);
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка проверки компиляции: " + e.getMessage(), e);
        } finally {
            RunServiceImpl.deleteQuietly(dir);
        }
    }

    // ---------- операции ----------

    private void updateCode(ProjectModel pm, JavaParser parser, String nodeId, String payload) {
        Ref ref = Ref.parse(nodeId);
        ProjectModel.MethodRef m = pm.method(ref.methodId);
        BlockStmt snippet = parseSnippet(parser, payload);
        if (ref.path == null) { // правим тело метода целиком
            BlockStmt body = m.body();
            if (body == null) throw new IllegalArgumentException("У метода нет тела");
            body.setStatements(snippet.getStatements());
        } else {
            StmtSlots.Loc loc = StmtSlots.locate(m.body(), ref.path);
            if (snippet.getStatements().isEmpty()) throw new IllegalArgumentException("Пустой код узла");
            List<Statement> news = new ArrayList<>(snippet.getStatements());
            loc.list().set(loc.index(), news.get(0));
            for (int i = 1; i < news.size(); i++) loc.list().add(loc.index() + i, news.get(i));
        }
        pm.touch(m.file());
    }

    private void delete(ProjectModel pm, String nodeId) {
        Ref ref = Ref.parse(nodeId);
        if (ref.path == null) throw new IllegalArgumentException("Нельзя удалить метод целиком через граф");
        ProjectModel.MethodRef m = pm.method(ref.methodId);
        StmtSlots.Loc loc = StmtSlots.locate(m.body(), ref.path);
        loc.list().remove(loc.index());
        pm.touch(m.file());
    }

    private void insert(ProjectModel pm, JavaParser parser, MutateRequest op) {
        Ref ref = Ref.parse(op.parentId());
        ProjectModel.MethodRef m = pm.method(ref.methodId);
        NodeList<Statement> list = containerList(m, ref);
        String code = op.payload() != null && !op.payload().isBlank() ? op.payload() : Templates.code(op.template());
        BlockStmt snippet = parseSnippet(parser, code);
        int idx = clamp(op.index(), list.size());
        for (Statement s : new ArrayList<>(snippet.getStatements())) list.add(idx++, s);
        pm.touch(m.file());
    }

    private void insertMethod(ProjectModel pm, JavaParser parser, MutateRequest op) {
        ProjectModel.MethodRef anchor = pm.method(op.parentId());
        ClassOrInterfaceDeclaration owner = anchor.decl().findAncestor(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("Метод можно добавить только в класс"));
        if (op.payload() == null || op.payload().isBlank()) throw new IllegalArgumentException("Не передан код метода");
        ParseResult<BodyDeclaration<?>> result = parser.parseBodyDeclaration(op.payload());
        if (!result.isSuccessful() || result.getResult().isEmpty()
                || !(result.getResult().get() instanceof com.github.javaparser.ast.body.MethodDeclaration)) {
            throw new IllegalArgumentException("Код нового метода не разобран");
        }
        owner.addMember(result.getResult().get());
        pm.touch(anchor.file());
    }

    private void move(ProjectModel pm, String nodeId, String parentId, Integer index) {
        Ref src = Ref.parse(nodeId);
        Ref dst = Ref.parse(parentId);
        if (src.path == null) throw new IllegalArgumentException("Нельзя переносить метод целиком");
        ProjectModel.MethodRef m = pm.method(src.methodId);
        if (!src.methodId.equals(dst.methodId)) throw new IllegalArgumentException("Перенос между методами не поддерживается");
        // нельзя перенести узел внутрь самого себя
        if (dst.path != null && (dst.path.equals(src.path) || dst.path.startsWith(src.path + "."))) {
            throw new IllegalArgumentException("Нельзя перенести узел внутрь самого себя");
        }
        StmtSlots.Loc from = StmtSlots.locate(m.body(), src.path);
        Statement moving = from.stmt();
        // фиксируем контейнер назначения ДО изъятия (после изъятия пути сдвигаются)
        Node holder = dst.path == null ? m.body() : StmtSlots.locate(m.body(), dst.path).stmt();
        String slot = dst.slot != null ? dst.slot : (dst.path == null ? null : StmtSlots.defaultSlot((Statement) holder));
        if (dst.path != null && slot == null) throw new IllegalArgumentException("Узел не является контейнером: " + parentId);

        Statement copy = moving.clone();
        from.list().remove(from.index());
        if (holder instanceof com.github.javaparser.ast.stmt.IfStmt condition && "e".equals(slot)
                && condition.getElseStmt().isEmpty()) condition.setElseStmt(new BlockStmt());
        NodeList<Statement> target = dst.path == null ? ((BlockStmt) holder).getStatements()
                : StmtSlots.write((Statement) holder, slot, true);
        target.add(clamp(index, target.size()), copy);
        pm.touch(m.file());
    }

    // ---------- вспомогательное ----------

    private static NodeList<Statement> containerList(ProjectModel.MethodRef m, Ref ref) {
        if (ref.path == null) {
            if (m.body() == null) throw new IllegalArgumentException("У метода нет тела");
            return m.body().getStatements();
        }
        Statement holder = StmtSlots.locate(m.body(), ref.path).stmt();
        String slot = ref.slot != null ? ref.slot : StmtSlots.defaultSlot(holder);
        if (slot == null) throw new IllegalArgumentException("Узел не является контейнером: " + ref.path);
        return StmtSlots.write(holder, slot, true);
    }

    private static BlockStmt parseSnippet(JavaParser parser, String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Не передан код");
        String text = code.strip();
        if (!(text.startsWith("{") && text.endsWith("}"))) text = "{\n" + text + "\n}";
        ParseResult<BlockStmt> r = parser.parseBlock(text);
        if (!r.isSuccessful() || r.getResult().isEmpty()) {
            String msg = r.getProblems().isEmpty() ? "ошибка разбора" : r.getProblems().get(0).getMessage();
            throw new IllegalArgumentException("Не удалось разобрать код узла: " + msg);
        }
        return r.getResult().get();
    }

    private static int clamp(Integer idx, int size) {
        if (idx == null) return size;
        return Math.max(0, Math.min(idx, size));
    }

    /** methodId [/ entry | /s<path>] [: slot] */
    private record Ref(String methodId, String path, String slot) {
        static Ref parse(String id) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Не указан узел");
            String slot = null;
            int colon = id.lastIndexOf(':');
            if (colon >= 0) {
                slot = id.substring(colon + 1);
                id = id.substring(0, colon);
            }
            int slash = id.lastIndexOf('/');
            if (slash < 0) return new Ref(id, null, slot);
            String tail = id.substring(slash + 1);
            String methodId = id.substring(0, slash);
            if (tail.equals(StmtSlots.ENTRY)) return new Ref(methodId, null, slot);
            if (!tail.startsWith("s") || tail.length() < 2) throw new IllegalArgumentException("Неверный ID узла: " + id);
            return new Ref(methodId, tail.substring(1), slot);
        }
    }
}
