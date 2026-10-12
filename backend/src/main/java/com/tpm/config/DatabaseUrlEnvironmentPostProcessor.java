package com.tpm.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts the de-facto standard {@code DATABASE_URL=postgres://user:pass@host:port/db?params} that Render,
 * Neon, Supabase, Railway and Heroku hand out, and maps it to Spring's datasource properties
 * (the JDBC driver doesn't accept credentials inside the URL). An explicit {@code DB_URL} always wins.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("DATABASE_URL");
        if (url == null || url.isBlank() || environment.containsProperty("DB_URL")) return;
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", toDataSourceProperties(url)));
    }

    static Map<String, Object> toDataSourceProperties(String databaseUrl) {
        URI uri = URI.create(databaseUrl.trim());
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgres:// or postgresql://");
        }
        var props = new LinkedHashMap<String, Object>();
        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + (uri.getPort() == -1 ? 5432 : uri.getPort())
                + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        props.put("spring.datasource.url", jdbc);
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            props.put("spring.datasource.username", decode(parts[0]));
            if (parts.length > 1) props.put("spring.datasource.password", decode(parts[1]));
        }
        return props;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
