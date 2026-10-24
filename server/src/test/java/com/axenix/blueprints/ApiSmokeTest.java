package com.axenix.blueprints;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Контекст поднимается с реальными сервисами, эндпоинты отвечают, ошибки разбора дают 422. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired MockMvc mvc;

    private static final String JAVA = "public class Main { public static void main(String[] a) { System.out.println(\\\"hi\\\"); } }";

    @Test
    void health() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void parseReturnsGraph() throws Exception {
        mvc.perform(post("/api/graph").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"Main.java\",\"source\":\"" + JAVA + "\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methods[0].methodId").value("Main.main"))
                .andExpect(jsonPath("$.methods[0].nodes[0].id").value("Main.main/entry"));
    }

    @Test
    void parseErrorIs422WithLine() throws Exception {
        mvc.perform(post("/api/graph").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"A.java\",\"source\":\"class A {\\n void m( {\\n}\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"));
    }

    @Test
    void mutateBreakingCompilationIs400() throws Exception {
        mvc.perform(post("/api/graph/mutate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"Main.java\",\"source\":\"" + JAVA + "\"}],"
                                + "\"action\":\"UPDATE_CODE\",\"nodeId\":\"Main.main/s1\",\"payload\":\"int x = \\\"s\\\";\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPILE_ERROR"));
    }

    @Test
    void analysisEndpointResponds() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"Main.java\",\"source\":\"" + JAVA + "\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.currentComplexity").exists());
    }

    @Test
    void runExecutesProgram() throws Exception {
        mvc.perform(post("/api/run").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"Main.java\",\"source\":\"" + JAVA + "\"}],\"stdin\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compiled").value(true))
                .andExpect(jsonPath("$.stdout").value(org.hamcrest.Matchers.startsWith("hi")));
    }

    @Test
    void verifyReportsPreservedBehavior() throws Exception {
        String f = "[{\"path\":\"Main.java\",\"source\":\"" + JAVA + "\"}]";
        mvc.perform(post("/api/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original\":" + f + ",\"modified\":" + f + ",\"mainClass\":\"Main\",\"tests\":[],\"mode\":\"EXACT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behaviorMatched").value(true));
    }
}
