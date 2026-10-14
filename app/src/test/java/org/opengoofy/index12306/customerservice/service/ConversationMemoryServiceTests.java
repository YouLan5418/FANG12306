package org.opengoofy.index12306.customerservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.chat.ChatClient;
import org.opengoofy.index12306.customerservice.chat.ChatMessage;
import org.opengoofy.index12306.customerservice.dao.entity.ChatMessageDO;
import org.opengoofy.index12306.customerservice.dao.entity.ChatSessionDO;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatMessageMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatSessionMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConversationMemoryService 单元测试：JDBC 直读、摘要水位线、会话创建。
 */
class ConversationMemoryServiceTests {

    private ChatMessageMapper chatMessageMapper;
    private ChatSessionMapper chatSessionMapper;
    private ChatClient chatClient;
    private RedissonClient redissonClient;
    private ConversationMemoryService service;

    @BeforeEach
    void setUp() throws Exception {
        chatMessageMapper = mock(ChatMessageMapper.class);
        chatSessionMapper = mock(ChatSessionMapper.class);
        chatClient = mock(ChatClient.class);
        redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        doReturn(false).when(lock).tryLock(anyLong(), any(TimeUnit.class));
        service = new ConversationMemoryService(chatMessageMapper, chatSessionMapper, chatClient, redissonClient);
    }

    @Test
    void loadHistory_emptySession_returnsEmptyList() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatSessionMapper.selectOne(any())).thenReturn(null);

        List<ChatMessage> messages = service.loadHistory("session1");

        assertTrue(messages.isEmpty());
    }

    @Test
    void loadHistory_returnsMessagesFromDb() {
        ChatMessageDO msg = new ChatMessageDO();
        msg.setId(1L);
        msg.setSessionId("session1");
        msg.setRole("user");
        msg.setContent("你好");

        when(chatMessageMapper.selectList(any())).thenReturn(List.of(msg));
        when(chatSessionMapper.selectOne(any())).thenReturn(null);

        List<ChatMessage> messages = service.loadHistory("session1");

        assertEquals(1, messages.size());
        assertEquals(ChatMessage.Role.USER, messages.get(0).getRole());
        assertEquals("你好", messages.get(0).getContent());
    }

    @Test
    void saveHistory_createsSessionAndInsertsMessages() {
        when(chatSessionMapper.selectOne(any())).thenReturn(null);

        List<ChatMessage> newMessages = List.of(
                ChatMessage.user("你好"),
                ChatMessage.assistant("你好，有什么可以帮您？")
        );
        service.saveHistory("session1", "user1", newMessages);

        verify(chatSessionMapper).insert(any());
        verify(chatMessageMapper, times(2)).insert(any());
    }

    @Test
    void loadHistory_withSummary_returnsSummaryAndFilteredMessages() {
        ChatSessionDO session = new ChatSessionDO();
        session.setSessionId("session1");
        session.setSummary("用户询问了退票规则");
        session.setSummaryMessageId(5L);

        // 水位线之后的消息（id > 5）
        ChatMessageDO msg = new ChatMessageDO();
        msg.setId(6L);
        msg.setRole("user");
        msg.setContent("那改签呢？");

        when(chatMessageMapper.selectList(any())).thenReturn(List.of(msg));
        when(chatSessionMapper.selectOne(any())).thenReturn(session);

        List<ChatMessage> messages = service.loadHistory("session1");

        // 第一条是摘要 system message，第二条是水位线之后的消息
        assertEquals(2, messages.size());
        assertEquals(ChatMessage.Role.SYSTEM, messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("退票规则"));
        assertEquals("那改签呢？", messages.get(1).getContent());
    }

    @Test
    void loadHistory_legacyOrphanToolMessage_isDiscarded() {
        ChatMessageDO msg = new ChatMessageDO();
        msg.setId(1L);
        msg.setRole("tool");
        msg.setContent("{\"result\":\"ok\"}");
        msg.setToolName("queryOrder");

        when(chatMessageMapper.selectList(any())).thenReturn(List.of(msg));
        when(chatSessionMapper.selectOne(any())).thenReturn(null);

        List<ChatMessage> messages = service.loadHistory("session1");

        assertTrue(messages.isEmpty());
    }
}