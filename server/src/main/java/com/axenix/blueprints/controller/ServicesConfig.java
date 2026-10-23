package com.axenix.blueprints.controller;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Services.*;
import com.axenix.blueprints.optimizer.DecomposeServiceImpl;
import com.axenix.blueprints.optimizer.LlmGateway;
import com.axenix.blueprints.optimizer.OptimizerServiceImpl;
import com.axenix.blueprints.parser.GraphEditServiceImpl;
import com.axenix.blueprints.parser.ParseServiceImpl;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.ProcessRunner;
import com.axenix.blueprints.sandbox.ReportStore;
import com.axenix.blueprints.sandbox.RunServiceImpl;
import com.axenix.blueprints.sandbox.VerifyServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

/** Реальные сервисы (профиль по умолчанию). Для заглушек запустите с --spring.profiles.active=stub. */
@Configuration
@Profile("!stub")
public class ServicesConfig {

    @Bean CompilerService compilerService() { return new CompilerService(); }

    @Bean ProcessRunner processRunner(@Value("${blueprints.runner.timeout-ms:3000}") long timeoutMs,
                                      @Value("${blueprints.runner.heap:256m}") String heap) {
        return new ProcessRunner(timeoutMs, heap);
    }

    @Bean ReportStore reportStore(@Value("${blueprints.report.path:verification_report.json}") String path) {
        return new ReportStore(path == null || path.isBlank() ? null : Path.of(path));
    }

    @Bean AnalyzerServiceImpl analyzerServiceImpl() { return new AnalyzerServiceImpl(); }

    @Bean AnalyzerService analyzerService(AnalyzerServiceImpl impl) { return impl; }

    @Bean ParseServiceImpl parseServiceImpl(AnalyzerServiceImpl analyzer) { return new ParseServiceImpl(analyzer); }

    @Bean GraphService graphService(ParseServiceImpl impl) { return impl; }

    @Bean GraphEditService graphEditService(ParseServiceImpl parse, CompilerService compiler) {
        return new GraphEditServiceImpl(parse, compiler);
    }

    @Bean LlmGateway llmGateway(@Value("${blueprints.llm.cache-dir:llm-cache}") String cacheDir,
                                @Value("${LLM_API_URL:}") String url,
                                @Value("${LLM_API_KEY:}") String key,
                                @Value("${LLM_MODEL:}") String model) {
        return new LlmGateway(Path.of(cacheDir), url, key, model);
    }

    @Bean OptimizerService optimizerService(AnalyzerServiceImpl analyzer, LlmGateway llm) {
        return new OptimizerServiceImpl(analyzer, llm);
    }

    @Bean DecomposeService decomposeService(CompilerService compiler) { return new DecomposeServiceImpl(compiler); }

    @Bean VerifyService verifyService(CompilerService compiler, ProcessRunner runner, AnalyzerServiceImpl analyzer,
                                      ReportStore reports) {
        return new VerifyServiceImpl(compiler, runner, analyzer, reports);
    }

    @Bean RunService runService(CompilerService compiler, ProcessRunner runner) {
        return new RunServiceImpl(compiler, runner);
    }
}
