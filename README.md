# mini-agent（第 4 阶段：会话记忆 + SSE）

在 RAG 之上加上会话表、最近 N 轮窗口、旧轮次摘要，以及拆开的 SSE 事件：`sources` / `token` / `done`。

## 准备

```powershell
$env:JAVA_HOME="D:\java17"
$env:Path="$env:JAVA_HOME\bin;" + $env:Path
$env:DEEPSEEK_API_KEY="sk-你的密钥"
cd C:\Users\11356\mini-agent
mvn spring-boot:run
```

会话存在本地 H2 文件 `./data/mini-agent.mv.db`（已 gitignore）。控制台：http://localhost:8080/h2-console 。

## 第 4 阶段验收

开会话：

```powershell
curl.exe -X POST http://localhost:8080/api/sessions
```

把返回的 `sessionId` 填进下面（示例用 `SID`）。

追问能接上主题（先报销，再问「那怎么申请」）：

```powershell
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"sessionId\":\"SID\",\"question\":\"报销咋整\"}"
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"sessionId\":\"SID\",\"question\":\"那怎么申请\"}"
```

第二条的 `rewrittenQuery` 应带上上一轮「报销」相关检索词；日志 `approxTokens=` 在多轮后应接近「摘要 + 最近 6 轮」，而不是按轮数直线上涨。

RAG 流式（先 sources，再 token，最后 done）：

```powershell
curl.exe -N -X POST http://localhost:8080/api/rag/stream -H "Content-Type: application/json" -d "{\"sessionId\":\"SID\",\"question\":\"一线城市住宿上限多少\"}"
```

## 记忆怎么工作

```
全量消息进 H2
     → 送给模型的只有：摘要 system + 最近 6 轮
     → 超出窗口的旧消息 LLM 压成摘要，并标 compacted
```

## 结构

- `POST /api/sessions`：创建会话
- `POST /api/rag`、`/api/rag/stream`：RAG；可选 `sessionId`
- `POST /api/chat`、`/api/chat/stream`：工具对话；可选 `sessionId`
- `session.max-recent-turns`：默认 6
