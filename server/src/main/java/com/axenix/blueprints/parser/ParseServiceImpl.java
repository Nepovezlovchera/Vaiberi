package com.axenix.blueprints.parser;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.CodeIssue;
import com.axenix.blueprints.model.Dto.GraphNode;
import com.axenix.blueprints.model.Dto.GraphResponse;
import com.axenix.blueprints.model.Dto.MethodGraphResponse;
import com.axenix.blueprints.model.Dto.NodeData;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Services.GraphService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Java → граф по каждому методу + метрики; узлы помечаются найденными проблемами. */
public class ParseServiceImpl implements GraphService {

    private final AnalyzerServiceImpl analyzer;

    public ParseServiceImpl(AnalyzerServiceImpl analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public GraphResponse graph(List<ProjectFile> files, String methodId) {
        return graph(ProjectModel.parse(files), methodId);
    }

    public GraphResponse graph(ProjectModel pm, String methodId) {
        AnalysisReport analysis = analyzer.analyze(pm);
        Map<String, List<String>> issuesByNode = new HashMap<>();
        for (CodeIssue i : analysis.issues()) {
            issuesByNode.computeIfAbsent(i.nodeId(), k -> new ArrayList<>()).add(i.id());
        }
        List<MethodGraphResponse> out = new ArrayList<>();
        for (ProjectModel.MethodRef m : pm.methods) {
            if (methodId != null && !methodId.isBlank() && !m.id().equals(methodId)) continue;
            MethodGraphResponse g = GraphBuilder.build(m);
            List<GraphNode> nodes = new ArrayList<>();
            for (GraphNode n : g.nodes()) {
                List<String> ids = issuesByNode.getOrDefault(n.id(), List.of());
                NodeData d = n.data();
                nodes.add(new GraphNode(n.id(), n.parentId(), n.type(), new NodeData(d.label(), d.code(),
                        d.lineStart(), d.lineEnd(), d.columnStart(), d.columnEnd(), !ids.isEmpty(),
                        ids.isEmpty() ? null : ids.get(0), ids, d.loopType(), d.condition(), d.header())));
            }
            out.add(new MethodGraphResponse(g.className(), g.methodId(), g.methodSignature(), g.fileName(),
                    g.lineStart(), g.lineEnd(), nodes, g.edges()));
        }
        if (out.isEmpty() && methodId != null && !methodId.isBlank()) {
            throw new IllegalArgumentException("Метод не найден: " + methodId);
        }
        return new GraphResponse(out, analysis.metrics());
    }
}
