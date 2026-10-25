package com.winwin.dataapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "INTERNAL_TOKEN=test-secret")
@AutoConfigureMockMvc
class TransformControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsMissingToken() throws Exception {
        mvc.perform(post("/api/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWrongToken() throws Exception {
        mvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transformsWithValidToken() throws Exception {
        mvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("OLLEH"));
    }

    @Test
    void rejectsBlankText() throws Exception {
        mvc.perform(post("/api/transform")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
