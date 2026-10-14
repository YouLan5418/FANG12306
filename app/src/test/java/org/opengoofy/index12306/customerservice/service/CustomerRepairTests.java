package org.opengoofy.index12306.customerservice.service;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.chat.*;
import org.opengoofy.index12306.customerservice.dao.entity.*;
import org.opengoofy.index12306.customerservice.dao.mapper.*;
import org.opengoofy.index12306.customerservice.fc.ToolRegistry;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.redisson.api.RedissonClient;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerRepairTests {
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final ChatSessionMapper sessions = mock(ChatSessionMapper.class);
    private final ChatClient client = mock(ChatClient.class);
    private final ConversationMemoryService memory = new ConversationMemoryService(messages, sessions, client, mock(RedissonClient.class));

    private ChatSessionDO ownedSession() {
        var session = new ChatSessionDO();
        session.setUsername("alice");
        session.setSessionId("session1");
        return session;
    }

    @Test void foreignSessionCannotBeLoadedOrAppended() {
        when(sessions.selectOne(any())).thenReturn(ownedSession());
        assertThrows(ClientException.class, () -> memory.prepareSession("session1", "bob"));
        assertThrows(ClientException.class, () -> memory.loadHistory("session1", "bob"));
        assertThrows(ClientException.class, () -> memory.saveHistory("session1", "bob", List.of(ChatMessage.user("attack"))));
        verifyNoInteractions(messages, client);
    }

    @Test void missingIdentityCannotSaveHistory() {
        assertThrows(ClientException.class, () -> memory.saveHistory("session1", null, List.of(ChatMessage.user("attack"))));
        verifyNoInteractions(messages, sessions);
    }

    @Test void toolProtocolAndFinalAnswerSurvivePersistence() {
        when(sessions.selectOne(any())).thenReturn(ownedSession());
        List<ChatMessageDO> stored = new ArrayList<>();
        when(messages.insert(any(ChatMessageDO.class))).thenAnswer(inv -> {
            ChatMessageDO message = inv.getArgument(0);
            message.setId((long) stored.size() + 1);
            stored.add(message);
            return 1;
        });
        var call = new ToolCall("call_123", "queryOrder", "{\"orderSn\":\"123\"}");
        memory.saveHistory("session1", "alice", List.of(ChatMessage.user("订单呢"),
                ChatMessage.assistantWithToolCalls("查询中", List.of(call)),
                ChatMessage.tool("call_123", "queryOrder", "{\"status\":10}"), ChatMessage.assistant("已支付")));
        when(messages.selectList(any())).thenAnswer(inv -> {
            var reversed = new ArrayList<>(stored);
            Collections.reverse(reversed);
            return reversed;
        });
        var history = memory.loadHistory("session1", "alice");
        assertEquals(4, history.size());
        assertEquals("call_123", history.get(1).getToolCalls().get(0).getId());
        assertEquals(call.getArguments(), history.get(1).getToolCalls().get(0).getArguments());
        assertEquals("call_123", history.get(2).getToolCallId());
        assertEquals("已支付", history.get(3).getContent());
        assertEquals("{\"status\":10}", stored.get(2).getToolResult());
    }

    @Test void truncatedToolSequenceIsNotSentToModel() {
        when(sessions.selectOne(any())).thenReturn(ownedSession());
        var request = new ChatMessageDO();
        request.setId(1L);
        request.setRole("assistant");
        request.setToolArgs("{\"toolCalls\":[{\"id\":\"c1\",\"name\":\"queryOrder\",\"arguments\":\"{}\"}]}");
        when(messages.selectList(any())).thenReturn(List.of(request));
        assertTrue(memory.loadHistory("session1", "alice").isEmpty());
    }

    @Test void everyRoundHasSystemRulesAndSavesAnswerBeforeDone() throws Exception {
        ConversationMemoryService mockedMemory = mock(ConversationMemoryService.class);
        when(mockedMemory.loadHistory("session1", "alice")).thenReturn(List.of(ChatMessage.user("上一问"), ChatMessage.assistant("上一答")));
        var properties = new ChatProperties();
        var prompt = mock(SystemPromptProvider.class);
        when(prompt.currentPrompt()).thenReturn("必须查询业务工具");
        var saved = new CountDownLatch(2);
        List<List<ChatMessage>> persisted = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            List<ChatMessage> turn = inv.getArgument(2);
            persisted.add(new ArrayList<>(turn));
            saved.countDown();
            return null;
        }).when(mockedMemory).saveHistory(anyString(), anyString(), anyList());
        when(client.streamChatCollect(any(), any())).thenAnswer(inv -> {
            ChatRequest request = inv.getArgument(0);
            assertEquals(ChatMessage.Role.SYSTEM, request.getMessages().get(0).getRole());
            assertEquals("必须查询业务工具", request.getMessages().get(0).getContent());
            StreamCallback callback = inv.getArgument(1);
            callback.onContent("最终答案");
            callback.onComplete();
            return ChatResponse.builder().content("最终答案").build();
        });
        var limiter = new AiConcurrencyLimiter(properties);
        var service = new CustomerServiceChatService(client, new ToolRegistry(List.of()), mock(ChatRateLimiter.class),
                mockedMemory, limiter, prompt, properties);
        try {
            service.streamChat("第一问", "session1", "alice");
            // 等待首轮完全释放名额再启动第二轮。
            verify(mockedMemory, timeout(3000)).saveHistory(anyString(), anyString(), anyList());
            properties.setUserMaxConcurrent(2);
            service.streamChat("第二问", "session1", "alice");
            assertTrue(saved.await(3, TimeUnit.SECONDS));
            for (var turn : persisted) {
                assertEquals(2, turn.size());
                assertEquals(ChatMessage.Role.USER, turn.get(0).getRole());
                assertEquals("最终答案", turn.get(1).getContent());
            }
        } finally { service.shutdown(); }
    }
}
