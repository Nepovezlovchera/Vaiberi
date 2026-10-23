package com.axenix.blueprints.model;

import com.axenix.blueprints.model.Dto.*;

import java.util.List;
import java.util.function.Consumer;

/** Границы ответственности: контроллер знает только интерфейсы, поэтому все работают параллельно. */
public final class Services {
    private Services() {}

    /** D3 */
    public interface GraphService { GraphResponse graph(List<ProjectFile> files, String methodId); }
    /** D4 */
    public interface GraphEditService { MutateResponse mutate(MutateRequest request); }
    /** D4 */
    public interface AnalyzerService { AnalysisReport analyze(List<ProjectFile> files); }
    /** D4 */
    public interface OptimizerService { OptimizeResponse optimize(OptimizeRequest request); }
    /** D3 + D4 */
    public interface DecomposeService { DecomposeResponse decompose(DecomposeRequest request); }
    /** D5. log получает строки терминала по мере выполнения (SSE). */
    public interface VerifyService {
        VerificationReport verify(VerifyRequest request, Consumer<String> log);
        default VerificationReport verify(VerifyRequest request) { return verify(request, line -> { }); }
    }
    /** D5 */
    public interface RunService { RunResponse run(RunRequest request); }
}
