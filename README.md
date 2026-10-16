# mini-agent（第 3 阶段：最小 RAG）

在 Chat + Tool Calling 之上，增加「文档 → 切块 → 向量检索 → 带出处生成」。知识来自 `src/main/resources/knowledge/`，不是模型参数里的常识。

## 准备

```powershell
$env:JAVA_HOME="D:\java17"
$env:Path="$env:JAVA_HOME\bin;" + $env:Path
$env:DEEPSEEK_API_KEY="sk-你的密钥"
cd C:\Users\11356\mini-agent
mvn spring-boot:run
```

Key 只走环境变量，不要写进 `application.yml`。

## RAG 验收调用

文档里有的（差旅报销，口语会先改写成「差旅费报销流程」）：

```powershell
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"question\":\"报销咋整\"}"
```

应能答到时限/交通标准，并在答案里带 `[C1]` 这类编号；`sources` 里能看到 `travel-reimbursement.md`。

文档里没有的（应拒绝编造）：

```powershell
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"question\":\"年终奖怎么发\"}"
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"question\":\"股权激励何时归属\"}"
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"question\":\"住房公积金如何补缴\"}"
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"question\":\"团建去三亚能报销吗\"}"
curl.exe -X POST http://localhost:8080/api/rag -H "Content-Type: application/json" -d "{\"question\":\"试用期多久转正\"}"
```

答错时先看日志 `rag question=... hits=`：相似度高但答案仍胡编，是生成没用资料；hits 就不相关，是检索/切块问题。

## 链路

```
问题 → QueryRewriter（可选改写）
     → 哈希向量（内存，无 Embedding API）
     → 余弦 TopK=5
     → generate()：只根据资料回答，没有就说无法确定
```

PDF 用 Apache Tika 解析：把 `.pdf` 放到 `knowledge/` 即可，启动时一并入库。当前示例是 Markdown。

## 结构

- `POST /api/rag`：最小 RAG
- `POST /api/chat`：第 2 阶段工具循环（天气），与 RAG 分开
- `ChatClient.generate`：单轮补全、不带 tools，避免 RAG 误调天气
- 检索是单路向量，没有 RRF / 图谱（那是后续阶段）
