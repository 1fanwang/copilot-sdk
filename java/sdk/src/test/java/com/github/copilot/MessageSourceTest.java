/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *--------------------------------------------------------------------------------------------*/

package com.github.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.copilot.rpc.AgentMode;
import com.github.copilot.rpc.Attachment;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;

class MessageSourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"user", "system", "command-review", "schedule-42", "agent-helper", " custom ", ""})
    void sendPreservesSourceAndOtherOptions(String source) throws Exception {
        try (var server = new MessageServer();
                var client = new CopilotClient(new CopilotClientOptions().setCliUrl(server.url()));
                var session = client.createSession(new SessionConfig().setSessionId("source-test")
                        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)).get(5, TimeUnit.SECONDS)) {
            for (String mode : List.of("enqueue", "immediate")) {
                var options = options(mode);
                assertNull(options.getSource());
                if (source != null) {
                    assertSame(options, options.setSource(source));
                }
                assertEquals("message-id", session.send(options).get(5, TimeUnit.SECONDS));
                assertRequest(server, options, source);
            }
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"user", "system", "command-review", "schedule-42", "agent-helper", " custom ", ""})
    void sendAndWaitPreservesClonedSourceAndOtherOptions(String source) throws Exception {
        try (var server = new MessageServer();
                var client = new CopilotClient(new CopilotClientOptions().setCliUrl(server.url()));
                var session = client.createSession(new SessionConfig().setSessionId("source-test")
                        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)).get(5, TimeUnit.SECONDS)) {
            for (String mode : List.of("enqueue", "immediate")) {
                var original = options(mode).setSource(source);
                var copy = original.clone();
                assertEquals(MAPPER.valueToTree(original), MAPPER.valueToTree(copy));
                original.setSource("agent-other");
                assertEquals(source, copy.getSource());

                assertNull(session.sendAndWait(copy, 5000).get(5, TimeUnit.SECONDS));
                assertRequest(server, copy, source);
            }
        }
    }

    private static MessageOptions options(String mode) {
        return new MessageOptions().setPrompt("Review this file").setDisplayPrompt("Review")
                .setAttachments(List.of(new Attachment("file", "/workspace/main.java", "Main"))).setMode(mode)
                .setAgentMode(AgentMode.PLAN).setRequestHeaders(Map.of("x-request-id", "trace-123"));
    }

    private static void assertRequest(MessageServer server, MessageOptions options, String source) throws Exception {
        JsonNode actual = server.requests.poll(5, TimeUnit.SECONDS);
        ObjectNode expected = MAPPER.valueToTree(options);
        expected.put("sessionId", "source-test");
        assertEquals(expected, actual);
        if (source == null) {
            assertFalse(actual.has("source"));
        } else {
            assertEquals(source, actual.path("source").asText());
        }
        assertFalse(actual.has("billable"), "Provenance must not override the default billing behavior");
    }

    /** Uses the same loopback JSON-RPC setup as BuiltinPluginDirectoriesTest. */
    private static final class MessageServer implements AutoCloseable {

        private final ServerSocket listener;
        private final Thread acceptThread;
        private final CompletableFuture<JsonRpcClient> ready = new CompletableFuture<>();
        private final LinkedBlockingQueue<JsonNode> requests = new LinkedBlockingQueue<>();

        MessageServer() throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            acceptThread = new Thread(this::accept, "message-source-runtime");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        String url() {
            return "127.0.0.1:" + listener.getLocalPort();
        }

        private void accept() {
            try {
                Socket socket = listener.accept();
                ready.complete(JsonRpcClient.fromSocket(socket, this::registerHandlers));
            } catch (IOException e) {
                ready.completeExceptionally(e);
            }
        }

        private void registerHandlers(JsonRpcClient rpc) {
            rpc.registerMethodHandler("connect",
                    (id, params) -> respond(rpc, id, Map.of("ok", true, "protocolVersion", 3, "version", "test")));
            rpc.registerMethodHandler("session.create",
                    (id, params) -> respond(rpc, id, Map.of("sessionId", "source-test")));
            rpc.registerMethodHandler("session.send", (id, params) -> {
                requests.add(params);
                respond(rpc, id, Map.of("messageId", "message-id"));
                try {
                    rpc.notify("session.event",
                            Map.of("sessionId", "source-test", "event", Map.of("id", UUID.randomUUID().toString(),
                                    "type", "session.idle", "timestamp", "2026-01-01T00:00:00Z", "data", Map.of())));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            rpc.registerMethodHandler("session.detach", (id, params) -> respond(rpc, id, Map.of("success", true)));
        }

        private static void respond(JsonRpcClient rpc, String id, Object result) {
            try {
                rpc.sendResponse(id, result);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws Exception {
            listener.close();
            acceptThread.join(5000);
            JsonRpcClient rpc = ready.getNow(null);
            if (rpc != null) {
                rpc.close();
            }
        }
    }
}
