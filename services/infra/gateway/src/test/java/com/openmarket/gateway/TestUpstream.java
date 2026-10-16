package com.openmarket.gateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * gateway 뒤의 서비스와 auth JWK 엔드포인트를 대신하는 HTTP 서버. JVM 하나에 하나만 띄운다.
 *
 * <p>{@code /oauth2/jwks}는 {@link TestJwt}의 공개 키를 주고, 나머지 경로는 받은 요청을 기록하고 200을 돌려준다.
 */
final class TestUpstream {

    static final TestUpstream INSTANCE = new TestUpstream();

    private final HttpServer server;
    private final AtomicReference<@Nullable Recorded> last = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();

    private TestUpstream() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/oauth2/jwks", exchange -> respond(exchange, TestJwt.jwkSetJson()));
        server.createContext(
                "/",
                exchange -> {
                    Headers headers = new Headers();
                    headers.putAll(exchange.getRequestHeaders());
                    last.set(
                            new Recorded(
                                    exchange.getRequestMethod(),
                                    exchange.getRequestURI().getRawPath(),
                                    headers));
                    hits.incrementAndGet();
                    respond(exchange, "{\"ok\":true}");
                });
        server.start();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** 마지막으로 서비스까지 온 요청. 없으면 실패한다. */
    Recorded last() {
        Recorded recorded = last.get();
        if (recorded == null) {
            throw new AssertionError("서비스까지 온 요청이 없다");
        }
        return recorded;
    }

    int hits() {
        return hits.get();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 서비스가 받은 요청. 헤더 이름은 대소문자를 가리지 않는다. */
    record Recorded(String method, String path, Headers headers) {

        @Nullable String header(String name) {
            return headers.getFirst(name);
        }
    }
}
