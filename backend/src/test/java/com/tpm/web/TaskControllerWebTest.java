package com.tpm.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tpm.common.Exceptions.ConflictException;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.config.AppProperties;
import com.tpm.config.AuthRateLimitFilter;
import com.tpm.config.SecurityConfig;
import com.tpm.task.TaskController;
import com.tpm.task.TaskService;

/** Web-layer slice: security rules, request validation and RFC 9457 error bodies. */
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, AuthRateLimitFilter.class})
@EnableConfigurationProperties(AppProperties.class)
class TaskControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean TaskService tasks;

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mvc.perform(get("/api/tasks/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesRequestBodies() throws Exception {
        mvc.perform(post("/api/projects/1/tasks").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").value("must not be blank"));
    }

    @Test
    void mapsNotFoundTo404ProblemDetail() throws Exception {
        when(tasks.get(42L)).thenThrow(new NotFoundException("Task", 42L));
        mvc.perform(get("/api/tasks/42").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task 42 not found"));
    }

    @Test
    void mapsOptimisticLockConflictTo409() throws Exception {
        when(tasks.update(eq(1L), any())).thenThrow(new ConflictException("WEB-1 was changed by someone else"));
        mvc.perform(put("/api/tasks/1").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"t\",\"priority\":\"LOW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void rejectsMalformedEnumValues() throws Exception {
        mvc.perform(post("/api/projects/1/tasks").with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"priority\":\"MEGA\"}"))
                .andExpect(status().isBadRequest());
    }
}
