# AI 客服混合模型验证说明

## 当前配置

后端使用“云端聊天模型 + 本地向量模型”的混合方案：

- Chat Provider：`deepseek`
- Chat Base URL：`https://api.deepseek.com`
- Chat Model：`deepseek-v4-flash`
- Chat API Key：从环境变量 `DEEPSEEK_API_KEY` 读取
- Embedding Base URL：`http://localhost:11434/v1`
- Embedding Model：`qwen3-embedding:0.6b`
- Embedding Dimension：`1024`
- Rerank：默认关闭，使用本地 `NoopRerankClient`，避免基础验证访问额外模型或外部重排序服务。

这样可以避免同时在 12G 显存里加载聊天模型和向量模型；本机只需要常驻 embedding 模型，聊天能力交给 DeepSeek API。

## 环境变量

确认 `DEEPSEEK_API_KEY` 已设置：

```powershell
echo $env:DEEPSEEK_API_KEY
```

项目也支持从根目录 `.env` 读取：

```properties
DEEPSEEK_API_KEY=sk-your-deepseek-api-key
```

`.env` 是本机私密文件，已被 Git 忽略；提交时只提交 `.env.example`。

IDEA 启动后端时也需要能读到该配置。如果 IDEA 读不到系统环境变量，可以优先使用根目录 `.env`，或在 Run Configuration 的 Environment variables 里添加：

```text
DEEPSEEK_API_KEY=你的 key
```

## Ollama Embedding 自检

本机仍需要启动 Ollama，并确保 `qwen3-embedding:0.6b` 可用：

```powershell
$env:OLLAMA_MODELS='D:\.ollama\models'
ollama serve
Get-NetTCPConnection -LocalPort 11434
ollama list
```

如果 `/api/tags` 返回空列表，通常是 Ollama HTTP 服务使用了错误的模型目录。当前机器的模型仓库目录为：

```text
D:\.ollama\models
```

该目录下应同时包含 `blobs` 和 `manifests`。

## 一键验证脚本

```powershell
powershell -ExecutionPolicy Bypass -File D:\12306\scripts\verify-ollama-ai.ps1
```

脚本会依次验证：

- Ollama `/api/tags` 能否列出本地模型。
- DeepSeek `/v1/chat/completions` 能否调用 `deepseek-v4-flash`。
- Ollama `/v1/embeddings` 能否调用 `qwen3-embedding:0.6b` 并输出向量维度。

也可以单独运行项目级 embedding 连通性测试：

```powershell
mvn -pl app -Dtest=OllamaEmbeddingConnectivityIT -Dollama.live=true test
```

通过时说明后端 `CloudEmbeddingClient` 已经能调用本地 `qwen3-embedding:0.6b` 并拿到 1024 维向量。

如果要验证其他模型，可以临时传参：

```powershell
powershell -ExecutionPolicy Bypass -File D:\12306\scripts\verify-ollama-ai.ps1 -ChatModel 实际模型名
```

## DeepSeek 云端连通性测试

项目内提供了一个默认跳过的 JUnit 集成测试：

```powershell
mvn -pl app -Dtest=DeepSeekConnectivityIT -Ddeepseek.live=true test
```

该测试只有在同时满足以下条件时才会真实访问 DeepSeek：

- JVM 参数包含 `-Ddeepseek.live=true`
- 当前进程能读取到环境变量 `DEEPSEEK_API_KEY`

如果后端日志出现：

```text
Authentication Fails (governor)
HTTP 401
```

优先检查 IDEA 的 Run Configuration 是否能读取 `DEEPSEEK_API_KEY`。系统环境变量刚添加后，已经打开的 IDEA 通常需要重启才能读取到。
如果使用 `.env`，确认 IDEA 的 Working directory 是项目根目录 `D:\12306`；如果 Working directory 是 `D:\12306\app`，当前配置也会尝试读取 `../.env` 对应的项目根目录文件。

## 后端基础验证

重启后端后，打开前端右下角智能客服浮窗，发送：

```text
退票规则是什么？
```

预期：

- 聊天请求走 DeepSeek API。
- 知识库检索仍使用本地 `qwen3-embedding:0.6b` 做向量化。
- 重排序不访问额外模型，直接按混合检索分数返回。
