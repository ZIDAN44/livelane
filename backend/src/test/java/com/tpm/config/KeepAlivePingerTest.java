package com.tpm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class KeepAlivePingerTest {

    @Test
    void pingsTheLivenessProbeOfTheConfiguredUrl() throws Exception {
        var paths = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            new KeepAlivePinger("http://127.0.0.1:" + server.getAddress().getPort() + "/").ping();
        } finally {
            server.stop(0);
        }
        assertThat(paths).containsExactly("/actuator/health/liveness");
    }

    @Test
    void unreachableHostIsLoggedNotThrown() {
        new KeepAlivePinger("http://127.0.0.1:1").ping();
    }

    @Test
    void failsFastWithoutUrl() {
        assertThatThrownBy(() -> new KeepAlivePinger(" ")).isInstanceOf(IllegalStateException.class);
        assertThat(new KeepAlivePinger("https://x.onrender.com//").target())
                .hasToString("https://x.onrender.com/actuator/health/liveness");
    }
}
