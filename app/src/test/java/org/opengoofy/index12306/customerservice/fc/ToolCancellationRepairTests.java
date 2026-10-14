package org.opengoofy.index12306.customerservice.fc;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.chat.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ToolCancellationRepairTests {
    @Test void timedOutToolIsInterrupted() throws Exception {
        var interrupted = new CountDownLatch(1);
        ToolExecutor tool = mock(ToolExecutor.class);
        when(tool.name()).thenReturn("slow");
        when(tool.execute(any(), any())).thenAnswer(inv -> {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return "{}";
        });
        var client = mock(ChatClient.class);
        when(client.streamChatCollect(any(), any()))
                .thenReturn(ChatResponse.builder().toolCalls(List.of(new ToolCall("c1", "slow", "{}"))).build())
                .thenReturn(ChatResponse.builder().content("超时了").build());
        StreamCallback callback = new StreamCallback() {
            public void onContent(String content) {}
            public void onComplete() {}
            public void onError(Throwable error) { fail(error); }
        };
        var messages = new ArrayList<ChatMessage>();
        new FunctionCallingEngine(client, new ToolRegistry(List.of(tool)), 3, 100)
                .run(messages, List.of(), callback, new ToolExecutionContext("alice", "1", "s"));
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertEquals("超时了", messages.get(messages.size()-1).getContent());
    }
}
