package com.tpm;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end against a real PostgreSQL (Testcontainers) and the real HTTP + WebSocket server:
 * seeded demo login, RBAC, drag & drop ordering, concurrent moves, and live STOMP events.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.rate-limit.capacity=1000", "app.reminders.cron=-"})
@Import(TestcontainersConfiguration.class)
class BoardIntegrationTest {

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void demoUserSeesSeededBoardAndViewerCannotEdit() throws Exception {
        var alice = login("alice@demo.tpm");
        var projects = call("GET", "/api/projects", alice, null);
        assertThat(projects.status()).isEqualTo(200);
        assertThat(projects.body().findValuesAsString("key")).contains("WEB", "APP");

        var board = call("GET", "/api/projects/1/board", alice, null).body();
        assertThat(board.get("columns")).hasSize(4);
        for (var column : board.get("columns")) {
            var positions = new ArrayList<String>();
            column.get("tasks").forEach(t -> positions.add(t.get("position").asString()));
            assertThat(positions).as("tasks are returned in position order").isSorted();
        }

        var victor = login("victor@demo.tpm");
        assertThat(call("GET", "/api/projects/1/board", victor, null).status()).isEqualTo(200);
        assertThat(call("POST", "/api/projects/1/tasks", victor, Map.of("title", "nope")).status()).isEqualTo(403);
        assertThat(call("GET", "/api/projects/2/board", victor, null).status()).isEqualTo(403);
    }

    @Test
    void dragAndDropOrderingAndConcurrentMovesStayConsistent() throws Exception {
        var alice = login("alice@demo.tpm");
        var project = call("POST", "/api/projects", alice,
                Map.of("name", "Ordering", "key", "ORD" + (System.nanoTime() % 1000))).body();
        long projectId = project.get("id").asLong();
        var columns = call("GET", "/api/projects/" + projectId + "/board", alice, null).body().get("columns");
        long todo = columns.get(0).get("id").asLong();

        var ids = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            var t = call("POST", "/api/projects/" + projectId + "/tasks", alice, Map.of("title", "T" + i, "columnId", todo));
            assertThat(t.status()).isEqualTo(201);
            ids.add(t.body().get("id").asLong());
        }
        // Move T4 to the top, then T0 after T2  =>  T4, T1, T2, T0, T3
        call("POST", "/api/tasks/" + ids.get(4) + "/move", alice, mapOf("columnId", todo, "afterTaskId", null));
        call("POST", "/api/tasks/" + ids.get(0) + "/move", alice, Map.of("columnId", todo, "afterTaskId", ids.get(2)));
        assertThat(titlesIn(projectId, alice, 0)).containsExactly("T4", "T1", "T2", "T0", "T3");

        // 20 concurrent drops into the same slot: column lock serialises them, no duplicate keys.
        var more = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) {
            more.add(call("POST", "/api/projects/" + projectId + "/tasks", alice,
                    Map.of("title", "C" + i, "columnId", todo)).body().get("id").asLong());
        }
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var id : more) {
                pool.submit(() -> {
                    start.await();
                    return call("POST", "/api/tasks/" + id + "/move", alice,
                            Map.of("columnId", todo, "afterTaskId", ids.get(4)));
                });
            }
            start.countDown();
        }
        var board = call("GET", "/api/projects/" + projectId + "/board", alice, null).body();
        var positions = new ArrayList<String>();
        board.get("columns").get(0).get("tasks").forEach(t -> positions.add(t.get("position").asString()));
        assertThat(positions).hasSize(25).doesNotHaveDuplicates().isSorted();
        assertThat(titlesIn(projectId, alice, 0).getFirst()).isEqualTo("T4");
    }

    @Test
    void boardChangesArePushedOverWebSocketAfterCommit() throws Exception {
        var alice = login("alice@demo.tpm");
        var bob = login("bob@demo.tpm");

        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter(json));
        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + bob);
        var session = stomp.connectAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);

        BlockingQueue<JsonNode> events = new ArrayBlockingQueue<>(10);
        session.subscribe("/topic/projects/1", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { events.offer((JsonNode) payload); }
        });
        Thread.sleep(500); // let the SUBSCRIBE reach the broker

        var created = call("POST", "/api/projects/1/tasks", alice, Map.of("title", "Live update!"));
        assertThat(created.status()).isEqualTo(201);

        JsonNode event;
        do {
            event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).as("expected a TASK_CREATED event").isNotNull();
        } while (!"TASK_CREATED".equals(event.get("type").asString()));
        assertThat(event.get("payload").get("title").asString()).isEqualTo("Live update!");
        assertThat(event.get("actorId").asLong()).isEqualTo(1L);
        session.disconnect();
    }

    // ---- helpers ----

    record Res(int status, JsonNode body) {}

    private List<String> titlesIn(long projectId, String token, int columnIndex) throws Exception {
        var titles = new ArrayList<String>();
        call("GET", "/api/projects/" + projectId + "/board", token, null).body()
                .get("columns").get(columnIndex).get("tasks").forEach(t -> titles.add(t.get("title").asString()));
        return titles;
    }

    private String login(String email) throws Exception {
        var res = call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Demo@1234"));
        assertThat(res.status()).isEqualTo(200);
        return res.body().get("accessToken").asString();
    }

    private Res call(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        var text = response.body();
        return new Res(response.statusCode(), text == null || text.isBlank() ? null : json.readTree(text));
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        var m = new java.util.HashMap<String, Object>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
