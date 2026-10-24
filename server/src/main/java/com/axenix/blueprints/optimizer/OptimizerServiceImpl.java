package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.CodeIssue;
import com.axenix.blueprints.model.Dto.OptimizeRequest;
import com.axenix.blueprints.model.Dto.OptimizeResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Services.OptimizerService;
import com.axenix.blueprints.parser.ProjectModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Оптимизация по проблеме из анализатора: сначала детерминированный шаблон, потом (если разрешено) LLM.
 * Результат НЕ считается верным сам по себе: клиент обязан прогнать его через /api/verify.
 */
public class OptimizerServiceImpl implements OptimizerService {

    private static final Pattern FENCE = Pattern.compile("```(?:java)?\\s*\\n(.*?)```", Pattern.DOTALL);

    private final AnalyzerServiceImpl analyzer;
    private final LlmGateway llm;

    public OptimizerServiceImpl(AnalyzerServiceImpl analyzer, LlmGateway llm) {
        this.analyzer = analyzer;
        this.llm = llm;
    }

    @Override
    public OptimizeResponse optimize(OptimizeRequest req) {
        if (req.issueId() == null || req.issueId().isBlank()) return optimizeAll(req.files());
        ProjectModel pm = ProjectModel.parse(req.files());
        AnalysisReport analysis = analyzer.analyze(pm);
        CodeIssue issue = analysis.issues().stream().filter(i -> i.id().equals(req.issueId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Проблема не найдена: " + req.issueId()));

        if (issue.type().equals("COSTLY_CALL_IN_LOOP") || issue.type().equals("NESTED_LOOP")) {
            Optional<String> done = TemplateFixes.apply(pm.units.get(issue.fileName()), issue.lineStart());
            if (done.isPresent()) {
                pm.touch(issue.fileName());
                return new OptimizeResponse(pm.render(), "TEMPLATE", done.get());
            }
        }
        if (req.allowLlm()) return viaLlm(req.files(), issue);
        throw new IllegalArgumentException("Для проблемы " + issue.type()
                + " нет автоматического исправления по шаблону. Разрешите LLM (allowLlm=true) или исправьте вручную.");
    }

    /** Кнопка «Оптимизировать» без выбранной проблемы: применяет все безопасные шаблонные исправления подряд. */
    private OptimizeResponse optimizeAll(List<ProjectFile> files) {
        List<ProjectFile> current = files;
        List<String> applied = new ArrayList<>();
        for (int round = 0; round < 20; round++) {
            ProjectModel pm = ProjectModel.parse(current);
            AnalysisReport analysis = analyzer.analyze(pm);
            boolean fixed = false;
            for (CodeIssue issue : analysis.issues()) {
                if (!issue.type().equals("COSTLY_CALL_IN_LOOP") && !issue.type().equals("NESTED_LOOP")) continue;
                Optional<String> done = TemplateFixes.apply(pm.units.get(issue.fileName()), issue.lineStart());
                if (done.isPresent()) {
                    pm.touch(issue.fileName());
                    current = pm.render();
                    applied.add(done.get());
                    fixed = true;
                    break;
                }
            }
            if (!fixed) break;
        }
        String text = applied.isEmpty()
                ? "Автоматически исправимых проблем (шаблоны) не найдено — код оставлен без изменений."
                : "Применено исправлений: " + applied.size() + ". " + String.join(" | ", applied);
        return new OptimizeResponse(current, "TEMPLATE", text);
    }

    private OptimizeResponse viaLlm(List<ProjectFile> files, CodeIssue issue) {
        ProjectFile target = files.stream().filter(f -> f.path().equals(issue.fileName())).findFirst().orElseThrow();
        String prompt = "Ты помогаешь оптимизировать Java-код без изменения поведения.\n"
                + "Проблема: " + issue.title() + " — " + issue.description() + "\n"
                + "Рекомендация: " + issue.recommendation() + "\n"
                + "Строки " + issue.lineStart() + "–" + issue.lineEnd() + " файла " + issue.fileName() + ".\n"
                + "Исправь ТОЛЬКО эту проблему. Сохрани сигнатуры, публичное поведение, формат вывода и порядок вывода. "
                + "Верни ПОЛНЫЙ исправленный текст файла в одном блоке ```java без пояснений.\n\n"
                + target.source();
        try {
            Optional<LlmGateway.Answer> ans = llm.ask(prompt);
            if (ans.isEmpty()) {
                throw new IllegalArgumentException("LLM не настроен (задайте LLM_API_KEY) и в кэше нет ответа для этой проблемы");
            }
            Matcher m = FENCE.matcher(ans.get().text());
            String code = m.find() ? m.group(1) : ans.get().text();
            ProjectModel.parse(List.of(new ProjectFile(target.path(), code))); // ответ должен быть валидным Java
            List<ProjectFile> out = new ArrayList<>();
            for (ProjectFile f : files) out.add(f.path().equals(target.path()) ? new ProjectFile(f.path(), code) : f);
            return new OptimizeResponse(out, ans.get().cached() ? "CACHED" : "LLM",
                    "Исправление предложено LLM. Обязательно проверьте его через Quality Gate (/api/verify).");
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка обращения к LLM: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к LLM прерван", e);
        }
    }
}
