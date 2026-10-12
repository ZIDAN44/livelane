package com.tpm.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps a free-tier instance warm on hosts that sleep after a few idle minutes (Render free: 15 minutes,
 * then a ~1 minute cold start). It requests the app's own <em>public</em> URL, so the request passes the
 * platform's edge and counts as inbound traffic. It hits the liveness probe, which never touches the
 * database, so a scale-to-zero Postgres such as Neon still suspends while nobody uses the app.
 */
@Component
@ConditionalOnBooleanProperty("app.keep-alive.enabled")
public class KeepAlivePinger {

    private static final Logger log = LoggerFactory.getLogger(KeepAlivePinger.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI target;

    public KeepAlivePinger(@Value("${app.keep-alive.url:}") String baseUrl) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Keep-alive is enabled but neither KEEP_ALIVE_URL nor RENDER_EXTERNAL_URL is set");
        }
        this.target = URI.create(baseUrl.strip().replaceAll("/+$", "") + "/actuator/health/liveness");
        log.info("Keep-alive enabled, pinging {}", target);
    }

    URI target() {
        return target;
    }

    @Scheduled(initialDelayString = "${app.keep-alive.interval}", fixedDelayString = "${app.keep-alive.interval}")
    public void ping() {
        var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30)).GET().build();
        try {
            int status = http.send(request, BodyHandlers.discarding()).statusCode();
            if (status != 200) log.warn("Keep-alive ping returned HTTP {}", status);
        } catch (IOException e) {
            log.warn("Keep-alive ping failed: {}", e.toString());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
