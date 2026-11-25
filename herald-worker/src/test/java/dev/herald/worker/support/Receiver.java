package dev.herald.worker.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * A scriptable webhook receiver built on the JDK HTTP server. Each path gets a behaviour that
 * maps the 1-based call number to a response, and the receiver records every request and the
 * peak concurrency seen per path.
 */
public final class Receiver implements AutoCloseable {

    public record Request(String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }
    }

    public record Reply(int status, long delayMillis, Map<String, String> headers) {
        public static Reply status(int status) {
            return new Reply(status, 0, Map.of());
        }

        public static Reply slow(int status, long delayMillis) {
            return new Reply(status, delayMillis, Map.of());
        }
    }

    private final HttpServer server;
    private final Map<String, IntFunction<Reply>> behaviours = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> peak = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public Receiver() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public void on(String path, IntFunction<Reply> behaviour) {
        behaviours.put(path, behaviour);
    }

    public List<Request> requests(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    public int peakConcurrency(String path) {
        return peak.getOrDefault(path, new AtomicInteger()).get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int call = calls.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        int now = inFlight.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        peak.computeIfAbsent(path, p -> new AtomicInteger()).accumulateAndGet(now, Math::max);
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(path, Map.copyOf(exchange.getRequestHeaders()), body));
            Reply reply = behaviours.getOrDefault(path, n -> Reply.status(200)).apply(call);
            if (reply.delayMillis() > 0) {
                Thread.sleep(reply.delayMillis());
            }
            reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), response.length);
            exchange.getResponseBody().write(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // The client gave up (e.g. request timeout); nothing to send.
        } finally {
            inFlight.get(path).decrementAndGet();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
