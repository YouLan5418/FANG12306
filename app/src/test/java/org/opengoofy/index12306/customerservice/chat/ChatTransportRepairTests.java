package org.opengoofy.index12306.customerservice.chat;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatTransportRepairTests {
    private HttpServer server(CountDownLatch finish) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                var stream = exchange.getResponseBody();
                stream.write("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                stream.flush();
                finish.await(5, TimeUnit.SECONDS);
                stream.write("data: {\"choices\":[{\"delta\":{\"content\":\"second\"}}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                stream.flush();
            } catch (Exception ignored) {
            } finally { exchange.close(); }
        });
        server.start();
        return server;
    }

    private OpenAIStyleChatClient client(HttpServer server) {
        var properties = new ChatProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTimeoutMs(4000);
        return new OpenAIStyleChatClient(properties, new ModelHealthStore(properties));
    }

    @Test void contentArrivesBeforeModelFinishesAndDoneIsNotRepeated() throws Exception {
        CountDownLatch finish = new CountDownLatch(1);
        CountDownLatch firstToken = new CountDownLatch(1);
        var server = server(finish);
        var client = client(server);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        StringBuffer text = new StringBuffer();
        AtomicInteger completed = new AtomicInteger();
        StreamCallback callback = new StreamCallback() {
            public void onContent(String token) { text.append(token); firstToken.countDown(); }
            public void onComplete() { completed.incrementAndGet(); }
            public void onError(Throwable error) { fail(error); }
        };
        try {
            Future<ChatResponse> response = executor.submit(() -> client.streamChatCollect(
                    ChatRequest.builder().messages(List.of(ChatMessage.user("hello"))).build(), callback));
            assertTrue(firstToken.await(3, TimeUnit.SECONDS));
            assertFalse(response.isDone());
            assertEquals("first", text.toString());
            finish.countDown();
            assertEquals("firstsecond", response.get(3, TimeUnit.SECONDS).getContent());
            assertEquals("firstsecond", text.toString());
            assertEquals(1, completed.get());
        } finally { finish.countDown(); server.stop(0); executor.shutdownNow(); client.shutdown(); }
    }

    @Test void sseTimeoutClosesActiveModelStream() throws Exception {
        CountDownLatch finish = new CountDownLatch(1);
        CountDownLatch firstToken = new CountDownLatch(1);
        var server = server(finish);
        var client = client(server);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var emitter = mock(SseEmitter.class);
        doAnswer(inv -> { firstToken.countDown(); return null; }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        var sender = new SseEmitterSender(emitter);
        sender.start();
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(timeout.capture());
        try {
            Future<ChatResponse> response = executor.submit(() -> client.streamChatCollect(
                    ChatRequest.builder().messages(List.of(ChatMessage.user("hello"))).build(), sender));
            assertTrue(firstToken.await(3, TimeUnit.SECONDS));
            timeout.getValue().run();
            assertTrue(sender.isCancelled());
            ExecutionException error = assertThrows(ExecutionException.class, () -> response.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, error.getCause());
        } finally { finish.countDown(); server.stop(0); executor.shutdownNow(); client.shutdown(); sender.onError(new RuntimeException("end")); }
    }

    @Test void completionCanBeDeferredUntilHistoryIsSaved() {
        var emitter = mock(SseEmitter.class);
        var sender = new SseEmitterSender(emitter);
        sender.deferCompletion();
        sender.onComplete();
        assertFalse(sender.isCompleted());
        verify(emitter, never()).complete();
        sender.finish();
        assertTrue(sender.isCompleted());
        verify(emitter).complete();
    }
}
