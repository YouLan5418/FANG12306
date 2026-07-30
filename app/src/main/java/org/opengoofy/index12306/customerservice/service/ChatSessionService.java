package org.opengoofy.index12306.customerservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.dao.entity.ChatMessageDO;
import org.opengoofy.index12306.customerservice.dao.entity.ChatSessionDO;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatMessageMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatSessionMapper;
import org.opengoofy.index12306.customerservice.dto.resp.ChatMessageRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.ChatSessionRespDTO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 会话管理服务：会话列表、消息详情、删除会话。
 * 所有操作均校验会话归属，防止越权访问他人会话。
 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 查询当前用户的会话列表，按更新时间倒序。
     */
    public List<ChatSessionRespDTO> listSessions(String username) {
        List<ChatSessionDO> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSessionDO>()
                        .eq(ChatSessionDO::getUsername, username)
                        .orderByDesc(ChatSessionDO::getUpdateTime));
        return sessions.stream()
                .map(s -> ChatSessionRespDTO.builder()
                        .sessionId(s.getSessionId())
                        .title(s.getTitle())
                        .updateTime(s.getUpdateTime())
                        .build())
                .toList();
    }

    /**
     * 查询会话消息详情。校验归属后返回，越权返回空列表。
     */
    public List<ChatMessageRespDTO> getMessages(String sessionId, String username) {
        if (!ownsSession(sessionId, username)) {
            return Collections.emptyList();
        }
        List<ChatMessageDO> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByAsc(ChatMessageDO::getId));
        return messages.stream()
                .map(m -> ChatMessageRespDTO.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .toolName(m.getToolName())
                        .toolArgs(m.getToolArgs())
                        .toolResult(m.getToolResult())
                        .createTime(m.getCreateTime())
                        .build())
                .toList();
    }

    /**
     * 删除会话及其全部消息。校验归属后删除，越权静默跳过。
     */
    public void deleteSession(String sessionId, String username) {
        ChatSessionDO session = getSession(sessionId);
        if (session == null || !username.equals(session.getUsername())) {
            return;
        }
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getSessionId, sessionId));
        chatSessionMapper.deleteById(session.getId());
    }

    private boolean ownsSession(String sessionId, String username) {
        ChatSessionDO session = getSession(sessionId);
        return session != null && username.equals(session.getUsername());
    }

    private ChatSessionDO getSession(String sessionId) {
        return chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionDO>()
                        .eq(ChatSessionDO::getSessionId, sessionId));
    }
}