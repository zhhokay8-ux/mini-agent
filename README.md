# mini-agent（第 1 阶段）

Spring Boot 封装 `ChatClient`，对外提供 `/api/chat`，内部调用 DeepSeek 的 OpenAI 兼容接口 `POST {base-url}/chat/completions`（不是 DeepSeek 文档里的业务路径 `/api/chat`；我们自己的服务才叫 `/api/chat`）。

## 准备

1. Java 17+
2. Maven 3.9+
3. 在 [DeepSeek 开放平台](https://platform.deepseek.com/api_keys) 创建 API Key

PowerShell：

```powershell
$env:DEEPSEEK_API_KEY="sk-你的密钥"
```

可选：

```powershell
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MODEL="deepseek-chat"
```

本机默认 `JAVA_HOME` 可能是 JDK 8，Spring Boot 3 必须用 **JDK 17**（本机可用 `D:\java17`）：

```powershell
$env:JAVA_HOME="D:\java17"
$env:Path="$env:JAVA_HOME\bin;" + $env:Path
```

## 启动

```powershell
cd C:\Users\11356\mini-agent
mvn spring-boot:run
```

## 调用

非流式：

```powershell
curl.exe -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"messages\":[{\"role\":\"user\",\"content\":\"用一句话介绍 RAG\"}]}"
```

流式（逐 token，`-N` 关闭缓冲）：

```powershell
curl.exe -N -X POST http://localhost:8080/api/chat/stream -H "Content-Type: application/json" -d "{\"messages\":[{\"role\":\"user\",\"content\":\"用一句话介绍 RAG\"}]}"
```

请求体：

```json
{
  "messages": [
    { "role": "system", "content": "你是简洁的助教" },
    { "role": "user", "content": "什么是 Token？" }
  ]
}
```

## 结构

- `ChatClient`：换供应商只改实现，Controller 不动
- `DeepSeekChatClient`：同步 `chat()` + SSE `stream()`
- Key 只走环境变量 / 配置，不要写进仓库
