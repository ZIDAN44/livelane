package com.tpm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** OpenAPI document served at /v3/api-docs with Swagger UI at /swagger-ui. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI tpmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TPM - Task & Project Management API")
                        .version("1.0")
                        .description("""
                                Mini Jira/Trello: projects, Kanban boards, tasks, comments, deadlines and notifications.

                                **Try it:** call `POST /api/auth/login` with `alice@demo.tpm` / `Demo@1234`, copy the
                                `accessToken`, click **Authorize** and paste it.

                                Real-time updates are pushed over STOMP/WebSocket at `/ws`
                                (topics: `/topic/projects/{id}`, `/user/queue/notifications`)."""))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
