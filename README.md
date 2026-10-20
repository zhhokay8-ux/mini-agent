# mini-agent（第 5 阶段：Agentic RAG + HITL）

检索不再是 `/api/rag` 一条死流水线，而是 Agent 可选的 `search_knowledge` 工具。循环里还有 `get_order`、`create_refund`（写操作必须人工确认）。

## 准备

```powershell
$env:JAVA_HOME="D:\java17"
$env:Path="$env:JAVA_HOME\bin;" + $env:Path
$env:DEEPSEEK_API_KEY="sk-你的密钥"
cd C:\Users\11356\mini-agent
mvn spring-boot:run
```

`max-iterations` 已改为 8。Key 只走环境变量。

## 验收（耳机退货）

都走 `POST /api/chat`，看响应里的 `toolTraces` 和 `confirmation`。

退货政策（应出现 `search_knowledge`）：

```powershell
curl.exe -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"messages\":[{\"role\":\"user\",\"content\":\"退货政策是什么\"}]}"
```

订单 1001 能否退（应先 `get_order` 再检索政策）：

```powershell
curl.exe -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"messages\":[{\"role\":\"user\",\"content\":\"订单 1001 能退吗\"}]}"
```

提交退货（应出现 `confirmation`，此时 `GET /api/refunds` 仍为空）：

```powershell
curl.exe -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"messages\":[{\"role\":\"user\",\"content\":\"帮我给订单 1001 提交退货\"}]}"
curl.exe http://localhost:8080/api/refunds
curl.exe -X POST http://localhost:8080/api/confirm -H "Content-Type: application/json" -d "{\"confirmationId\":\"上面返回的id\",\"approved\":true}"
curl.exe http://localhost:8080/api/refunds
```

确认前退货列表必须没有 1001；`approved:true` 之后才有退货单。

## 工具

| 工具 | 作用 |
| --- | --- |
| search_knowledge | 第 3 阶段向量检索，返回 chunks |
| get_order | 读模拟订单。1001 上周耳机可退，1002 超窗不可退 |
| create_refund | 只登记待确认，不写库 |
| get_weather | 第 2 阶段天气 |

固定 `/api/rag` 仍保留，适合对照「Workflow vs Agent」。
