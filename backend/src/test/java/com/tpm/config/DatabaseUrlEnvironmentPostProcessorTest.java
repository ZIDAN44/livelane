package com.tpm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsRenderStyleUrl() {
        var props = DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties(
                "postgresql://tpm:s3cr%40t@dpg-abc123-a/tpm");
        assertThat(props).containsEntry("spring.datasource.url", "jdbc:postgresql://dpg-abc123-a:5432/tpm")
                .containsEntry("spring.datasource.username", "tpm")
                .containsEntry("spring.datasource.password", "s3cr@t");
    }

    @Test
    void keepsPortAndQueryParametersLikeNeonSsl() {
        var props = DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties(
                "postgres://u:p@ep-x.eu-central-1.aws.neon.tech:6543/neondb?sslmode=require");
        assertThat(props.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-x.eu-central-1.aws.neon.tech:6543/neondb?sslmode=require");
    }

    @Test
    void explicitDbUrlWinsAndMissingUrlIsIgnored() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("DATABASE_URL", "postgres://u:p@h/db", "DB_URL", "jdbc:postgresql://other/db")));
        processor.postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getPropertySources().contains("databaseUrl")).isFalse();

        var empty = new StandardEnvironment();
        processor.postProcessEnvironment(empty, new SpringApplication());
        assertThat(empty.getPropertySources().contains("databaseUrl")).isFalse();
    }

    @Test
    void appliesWhenOnlyDatabaseUrlIsSet() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("DATABASE_URL", "postgres://u:p@h:5433/db")));
        processor.postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h:5433/db");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("u");
    }

    @Test
    void rejectsOtherSchemes() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("mysql://u:p@h/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
