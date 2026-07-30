package org.opengoofy.index12306.customerservice.fc;

import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表，Spring 自动注入所有 ToolExecutor bean。
 * 提供按名称查找和批量获取工具定义。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    public ToolRegistry(List<ToolExecutor> executors) {
        if (executors != null) {
            for (ToolExecutor executor : executors) {
                this.executors.put(executor.name(), executor);
                log.info("注册工具: {}", executor.name());
            }
        }
    }

    public ToolExecutor get(String name) {
        return executors.get(name);
    }

    /**
     * 获取所有已注册工具的定义列表，用于构建 LLM 请求。
     */
    public List<ToolDefinition> allDefinitions() {
        return executors.values().stream()
                .map(ToolExecutor::definition)
                .toList();
    }
}