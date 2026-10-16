# 从 Java Spring Boot 到 Agent 开发：以 Ragent 为路线图

> 对照仓库：[nageoffer/ragent](https://github.com/nageoffer/ragent)  
> 官方文档：[nageoffer.com/ragent](https://nageoffer.com/ragent)  
> 读者画像：会 Java / Spring Boot，想系统学会 **Agent 应用开发**，而不是去训模型。  
> 建议总周期：约 **8～12 周**（每天 1～2 小时，或周末集中）。宁可慢、每阶段有可演示产物，也不要一遍把整个仓库“看完”。

---

## 0. 先记住三句话

1. **你不需要先学 Python、微调、Transformer 推导。** 应用层 Agent 的核心是：把 LLM 当“会规划的控制器”，用工程把它接进检索、工具、记忆、鉴权、观测。
2. **Ragent 不是入门 Demo，是企业级对照实现。** 第一周不要从读完整个仓库开始。先自己做出最小闭环，再对照 Ragent 看“生产里多了什么”。
3. **Workflow ≠ Agent。** 固定步骤的知识问答用 Workflow（Ragent v1）；需要边想边决定“查库还是调工具、调几次、何时停”才用 Agent（Ragent v2 / ReAct）。

---

## 1. 这个项目是什么

**Ragent** 是一套用 Java 写的 **生产级 Agentic RAG 平台**：文档进知识库 → 提问理解 → 多路检索 → 生成带出处的答案；进阶路径上，模型还可以 **自己规划**，去检索知识、调用 MCP 工具、加载 Skills、在写操作前等人确认。

定位可以拆成两层：

| 层 | 名字 | 一句话 |
| --- | --- | --- |
| v1 | Workflow RAG | 路径写死：重写问题 → 意图 → 多路召回 → 组装 Prompt → 流式生成 |
| v2 | Agentic RAG | 路径由模型在 **ReAct 循环**里决定：检索、MCP、Skills 都是工具 |

作者刻意 **没有把主链路绑死在 Spring AI / LangChain4j** 上（官方有专文解释选型）：模型调用、检索、入库用自己的接口隔离；Agent 执行引擎用 **Alibaba AgentScope**（Java），工具协议用 **MCP Java SDK**。

当前仓库（以 GitHub `main` 与根 `pom.xml` 为准）大致是：

- **语言 / 框架**：Java 17，Spring Boot **4.x**（文档里 v1 段落仍可能写 3.5.x，以代码为准）
- **模块化单体**：7 个 Maven 模块 + React 18 控制台
- **数据**：PostgreSQL（业务库）；向量可用 pgvector 或 Milvus；可选 ES / Neo4j；Redis；RocketMQ；对象存储 S3/OSS
- **AI**：自研 `ChatClient` / `EmbeddingClient` / `RerankClient`；Agent 侧 AgentScope 2.x；MCP SDK ~0.17
- **工程**：Sa-Token、MyBatis-Plus、Redisson、SSE 流式、Trace、Langfuse、人工确认写操作

### 1.1 七个模块（读代码时的地图）

```
用户提问
   │
   ▼
bootstrap     启动与装配（几乎没有业务）
   │
   ├─ rag         v1：知识库、入库 Pipeline、意图树、混合检索、会话、管理 API
   ├─ agent       v2：ReAct、MCP 客户端、Skills、记忆、人工确认
   ├─ infra-ai    Chat / Embedding / Rerank / VLM、档位、路由、首包探测、熔断降级
   ├─ system      登录、审计
   ├─ framework   统一异常、幂等、SSE、MQ、分布式 ID……
   └─ mcp-server  独立示例工具服务（天气/票务/销售/联网搜索）
```

读代码顺序建议：**bootstrap 启动配置 → infra-ai 一次 Chat → rag 一次问答 → agent 一次 ReAct → mcp-server 一个 Tool。** 不要从 `frontend` 或所有设计模式清单开始。

### 1.2 一次提问在系统里怎么走（概念）

**v1 Workflow（可控、适合纯问答）**

```
用户问题
  → 查询词映射 / 问题重写 / 复杂问题拆分
  → 树形意图（闲聊 / 某知识库 / 需澄清）
  → 并行检索：向量 + 关键词 +（可选）图谱 +（可选）联网
  → 去重 → RRF 融合 → Rerank → 截断进 Prompt
  → 带最近 N 轮 + 摘要的上下文
  → 流式生成（SSE）+ 引用溯源 + 推荐追问
```

**v2 Agent（动态、适合“查完还要办事”）**

```
用户任务
  → ReAct 循环：Thought → Action(工具) → Observation → …
  → 工具可以是：知识检索、MCP 远程工具、Skills 手册
  → 上下文水位裁剪 / 摘要压缩 / 长期记忆
  → 写操作：先展示参数，人工确认后再执行
  → 思考过程、工具调用、耗时对用户可见（并可进 Langfuse）
```

官方举过一个该走 Agent 的例子：用户说「上周买的耳机能退吗？能退就帮我申请」——要先确认订单、查订单状态、检索退货政策、不够就追问、符合再确认后调售后接口。这不是一次 RAG 能结束的。

---

## 2. 你要掌握的知识地图（理论，按优先级）

下面每一项都写了 **“懂到什么程度就算过关”**。不要平均用力。

### P0：没有这些，写不出 Agent

#### 2.1 大模型调用（应用视角）

要懂：

- Chat Completions：`messages` 角色（system / user / assistant / tool）
- **Token** 是计费与上下文窗口的单位；超窗会截断或失败
- **温度、max tokens、流式（SSE/chunk）**
- **Function Calling / Tool Calling**：模型不直接执行代码，只输出“要调哪个函数、参数 JSON”；**执行权在你的服务端**
- Embedding：文本 → 向量；同一套模型空间才能算相似度
- 幻觉：模型会编。RAG/工具是为了 **把事实从系统里取出来再让模型组织语言**

过关标准：能自己用 HTTP 调通一个国内兼容 OpenAI 的接口（通义、DeepSeek、硅基流动、Ollama 任选），并画出「请求 JSON → 模型 → 流式 token」时序图。

不必先懂：注意力公式、预训练、LoRA。以后工作要用再补。

#### 2.2 RAG（Retrieval-Augmented Generation）

要懂整条链，而不是“向量库 + Chat”四个字：

| 环节 | 你要能讲清的问题 |
| --- | --- |
| 解析 | PDF/Word 表格、扫描件、乱码怎么变成干净文本（Ragent 用 Apache Tika） |
| 分块 | 太大不准、太小丢上下文；按段落 / 固定长度 / 语义切 |
| 索引 | 向量索引 vs 关键词（BM25）；订单号这类精确值为什么向量会失败 |
| 检索 | TopK、相似度阈值；多路召回为什么需要 **RRF** 和 **Rerank** |
| 生成 | 如何把 chunk 塞进 Prompt、如何要求 **引用出处**、如何减少胡编 |
| 评测 | 召回差还是生成差？没有评测就是玄学调参 |

过关标准：用自己的 3～5 篇文档，做出「提问 → 命中相关段落 → 答案带来源」；能解释一次检索失败是切块问题还是 query 太口语。

#### 2.3 Agent 与 ReAct

要懂：

- **Agent** = LLM + **循环** + **工具** + **停止条件**（不是“多调几次 API”）
- **ReAct**（Reason + Act）：先推理，再选工具，观察结果，再决定下一步
- **Workflow vs Agent**：路径固定用 Workflow（便宜、可测、可控）；路径依赖中间结果用 Agent（贵、慢、难测，但灵活）
- **停止条件**：最大步数、已得到最终答案、用户取消、需要人工确认
- **人机协同（HITL）**：转账、下单、改库必须确认，Ragent v2 明确做了这点

经典论文/概念（读摘要即可）：ReAct；Toolformer / Function Calling；MCP 规范首页。

过关标准：能在白板上画出 ReAct 循环，并指出「工具失败、选错工具、死循环」三种故障各怎么收。

#### 2.4 MCP（Model Context Protocol）

要懂：

- MCP 把「工具的 Schema + 调用」标准化，Agent 运行时 **发现** 远程工具，而不是把每个 HTTP 接口写死在 Prompt 里
- 分成 MCP Server（暴露 tools）和 Client（Agent 侧）
- 写操作要校验参数、鉴权、确认（Ragent：意图树里仍可配置确认规则）

过关标准：自己写一个 MCP Server 暴露 1～2 个只读工具（例如查天气或查本地文件元数据），用任意 Client 调通。

### P1：做出「能上线的味道」

- **会话记忆**：最近 N 轮 + 超长则摘要；Agent 还有工具结果堆积，要 **按水位裁剪**
- **长期记忆**：跨会话沉淀用户偏好/事实（可选，Ragent v2 有）
- **Skills**：按任务加载说明书，避免 system prompt 无限膨胀
- **模型容错**：多供应商、超时、空响应、熔断降级（Ragent `infra-ai`）
- **可观测**：一次 Agent Run 的每一步 Thought/Tool/Token/耗时（Langfuse / 自研 Trace）
- **并发与限流**：模型很慢很贵，不能用普通 HTTP 线程池硬扛（Ragent：Redis 公平排队、专用线程池、TTL 透传）

### P2：对照 Ragent 再学（有余力）

- 树形意图、查询词映射、问题拆分
- 知识图谱通道、入库 Pipeline 节点编排、远程文档刷新状态机
- RocketMQ 事务消息保证分块可靠
- 管理后台、反馈、Grounding Chunk 原文预览

这些让你「面试能讲企业复杂度」，但 **不是你做第一个 Agent 的前置**。

---

## 3. 和你已有技能怎么接

你已经会的，直接复用：

| Spring Boot 经验 | 在 Agent 项目里对应什么 |
| --- | --- |
| Controller + DTO | 对话 API、管理 API、确认写操作的回调 |
| Service 分层 | RAG 编排 / Agent 循环不要塞进 Controller |
| 策略、责任链、适配器 | 多检索通道、多模型方言、检索后处理（Ragent 大量使用） |
| Redis | 会话、限流排队、分布式锁 |
| 消息队列 | 入库异步、可靠分块 |
| SSE / WebSocket | 流式打字、思考过程、工具调用时间线 |
| 统一异常、鉴权 | 工具调用的权限与租户隔离 |

你要新练的：

- 把 **不确定性**（模型输出）变成 **确定的状态机**（解析 tool call、校验 JSON、重试、停）
- Prompt 与上下文是 **产品的一部分**，要版本化、可测，而不是字符串拼接完事
- 评测集：10～50 条黄金问答，比再换一个“更强模型”更重要

---

## 4. 分阶段学习计划（一步一步，带验收）

原则：**每阶段结束都有一个你能录屏演示的东西。** 阶段通过后再读 Ragent 对应模块。

---

### 第 0 周：环境与心智（1～3 天）

**目标**：能跑官方体验或最小本地依赖，建立「这不是 ChatGPT 套壳」的直觉。

做什么：

1. 打开 [在线体验](https://nageoffer.com/ragent)（若可用）或阅读 README 架构图，只看 v1/v2 差异。
2. 申请一个 Chat + Embedding 的 API Key（或本地 Ollama：一个 chat 模型 + 一个 embedding 模型）。
3. 用 curl / Postman 打通一次非流式 Chat、一次流式 Chat。

**理论**：Token、上下文窗口、流式。

**验收**：

- [ ] 能说清 v1 和 v2 各自适合什么场景
- [ ] 本地或云上成功完成一次 Chat Completions

**不要做**：一上来 clone Ragent 配齐 PG + Redis + MQ + Milvus + ES。依赖过多会劝退。

---

### 第 1 阶段：最小 Chat 服务（约 3～5 天）

**目标**：Spring Boot 里封装「对话」，而不是在 Controller 里堆 HTTP 代码。

动手做一个小项目，例如 `mini-agent`：

1. `ChatClient` 接口：`chat(List<Message>)`、`stream(...)`
2. 一个 OpenAI 兼容实现（OkHttp / WebClient）
3. 一个 `/api/chat`：入参 messages，出参 assistant 文本；可选 SSE

**理论**：message 角色；system prompt 的作用（人设 vs 规则 vs 知识——知识不要全塞 system）。

**验收**：

- [ ] 换 Key / Base URL 只改配置
- [ ] 流式输出在浏览器或 curl `-N` 能看到逐 token
- [ ] 你能讲清：前端看到的“思考”如果只是模型文本，和真正的 tool 轨迹有何不同

**对照 Ragent**：浏览 `infra-ai` 的客户端抽象和「档位 / 降级」目录，**先不要实现熔断**，只知道生产还要这些。

---

### 第 2 阶段：Tool Calling（约 1 周）——Agent 的第一块肌肉

**目标**：模型输出「要调工具」，你的 Java 代码执行后把结果塞回 messages，再让模型说话。

做一个天气或「查询本地订单 mock」工具：

```
用户 → 模型（可能返回 tool_calls）
     → 你的服务执行 tool
     → 把 tool 结果作为 role=tool 的消息
     → 再调模型
     → 最终自然语言回答
```

**理论**：JSON Schema 描述工具；模型可能 **编造参数**；必须服务端校验。循环要有 `maxIterations`（例如 5）。

**验收**：

- [ ] 问「北京天气」会走工具，问「什么是 RAG」不走工具
- [ ] 故意让工具抛错，模型能收到错误信息并道歉/换策略，而不是进程 500 直接挂
- [ ] 日志里能打印：第几轮、调了哪个 tool、参数、耗时（不要打印密钥）

这就是 **单工具 ReAct 雏形**。Ragent v2 是这个循环的工程加强版（多工具、MCP、记忆、确认）。

---

### 第 3 阶段：最小 RAG（约 1.5～2 周）

**目标**：知识来自你的文档，而不是模型参数。

建议技术（保持 Java）：

- 解析：先 TXT/Markdown，再试一个 PDF（Tika）
- 切块：按字符重叠切（例如 500 字、重叠 50）
- 向量：PostgreSQL + pgvector **或** 内存 list 先跑通（数据少时）
- 检索：cosine TopK=5
- 生成：Prompt 写明「只根据下列资料回答，没有就说不知道，并列出资料编号」

**理论**：embedding 空间；为什么口语 query 要 **改写**（本阶段可先手写改写：把「报销咋整」变成「差旅费报销流程」）。

**验收**：

- [ ] 文档里有的制度能答对且带来源
- [ ] 文档里没有的会拒绝编造（抽 5 题测幻觉）
- [ ] 能讲一次「答错」是检索没召回还是召回了但模型没用

**对照 Ragent `rag` 模块**：只看「单路向量检索 + 生成」相关类。混合检索、图谱留到第 6 阶段。

---

### 第 4 阶段：会话与流式产品形态（约 3～5 天）

**目标**：像个产品，而不像一次 HTTP。

做：

- 会话表：session_id、messages
- 只把最近 N 轮塞进模型；超出则用 LLM 做一段摘要当 system 附加
- SSE 事件拆开：`token` / `sources` / `done`（对照 Ragent 的分事件思想）

**验收**：

- [ ] 连续追问「那怎么申请」能接上上一轮主题
- [ ] 20 轮后请求体不会无限膨胀（看日志里的 token 估算即可）

**对照 Ragent**：会话记忆 + Trace 字段（输入输出、耗时）。

---

### 第 5 阶段：真正的 Agent = RAG 作为工具（约 1～2 周）

**目标**：检索不再是固定流水线的一步，而是 Agent **可选的一个 tool**。

循环里至少两个工具：

1. `search_knowledge(query)` → 走你的 RAG 检索，返回 chunk 列表
2. `get_order(orderId)` 或任意业务 mock

再加规则：

- `maxSteps = 8`
- 禁止在未 search 的情况下长篇回答制度类问题（可用 prompt 约束，不要迷信）
- 对 `create_refund` 这类写工具：先返回 `need_confirm`，前端点确认再执行

**理论**：Agentic RAG；HITL；工具过多时模型会选错（后面用 Skills / 意图缩小工具集）。

**验收（用官方耳机退货故事改编一套用例）**：

- [ ] 「退货政策是什么」→ 主要走知识检索
- [ ] 「订单 1001 能退吗」→ 先查订单再查政策
- [ ] 「帮我提交退货」→ 必须出现确认步骤，确认前数据库无写入

**对照 Ragent `agent` 模块 + 人工确认截图逻辑。** 到这里，你已经「会做 Agent」了，Ragent 是把每一步做成可运营系统。

---

### 第 6 阶段：MCP 与 Skills（约 1 周）

**目标**：工具从「写死在应用里的 Java 方法」变成「可插拔服务」。

做什么：

1. 把第 2 阶段的工具抽到独立进程，用 MCP Java SDK 暴露（可直接读 Ragent `mcp-server`）
2. Agent 启动时从 `agent.mcp.servers` 这类配置 **发现** tools
3. 写一份 Skill 文档（Markdown）：什么场景用哪些工具、禁止事项；只在匹配任务时注入

**验收**：

- [ ] 不改 Agent 引擎代码，只启动新 MCP Server 就能多一个工具
- [ ] Skill 关闭时，模型不再看到那套工具说明（或工具被遮蔽）

**对照 Ragent**：AgentScope 发现 MCP；中间件做记忆注入、上下文压缩、技能遮蔽。

---

### 第 7 阶段：对照读 Ragent 源码（约 2 周，可并行做笔记）

现在才 **clone 并尝试精简启动**（按官方「快速启动」，能减依赖就减：先 PG+Redis+一个模型，向量用 pgvector）。

建议阅读顺序与问题清单：

| 顺序 | 模块 | 带着这些问题读 |
| --- | --- | --- |
| 1 | `bootstrap` | 配置项如何把 rag/agent 装配起来？ |
| 2 | `infra-ai` | Chat 失败如何探测首包、如何换候选模型？ |
| 3 | `rag` 入库 | Pipeline 节点如何编排？分块结果如何进向量库？ |
| 4 | `rag` 检索 | `SearchChannel`、RRF、Rerank 接口在哪？单通道失败为何不拖死整链？ |
| 5 | `rag` 意图/重写 | 意图树节点类型？什么时候向用户澄清？ |
| 6 | `agent` | ReAct 循环、中间件链、确认、记忆水位 |
| 7 | `mcp-server` | 一个 tool 的 Schema 怎么写？ |
| 8 | `framework` | SSE 取消、幂等，和 Agent 长请求的关系 |
| 9 | `frontend`（可选） | 时间线如何渲染 thought / tool / confirm |

**验收**：

- [ ] 能向别人讲 20 分钟：Ragent 一次 v2 请求的调用链
- [ ] 能指出 3 处你自己 mini 项目欠缺、而 Ragent 用设计模式解决的点
- [ ] （加分）改一个扩展点：新检索通道 **或** 新 MCP 工具 **或** 新人设 Skill，跑通测试

官方质量叙事里强调的设计模式，用你自己的话各举一个代码位置即可，不要背表格。

---

### 第 8 阶段：做「你自己的 Agent」产品（持续）

不要再做一个通用「企业知识库」。选一个 **你熟悉的业务窄场景**，例如：

- 个人：本地笔记 / 博客仓库问答 + 创建 issue 前确认
- 工作：工单查询 + 知识库 + 仅允许查、不允许改（或改必须确认）
- 学习：针对 Ragent 文档本身的问答助手（dogfooding）

产品必须包含：

1. 范围说明（不会什么）
2. 工具列表与权限
3. 10 条评测问答（定期回归）
4. 基本观测（每次 tool 的成功率和平均步数）

**验收**：一个陌生人按 README 能在 30 分钟内跑起来并完成 3 个场景。

---

## 5. 每周时间表示例（12 周版）

| 周 | 主题 | 周末演示 |
| --- | --- | --- |
| 1 | Chat 封装 + 流式 | 网页里打字机效果 |
| 2 | Tool Calling + 最大步数 | 天气工具 + 失败重试 |
| 3～4 | 解析切块向量检索 | 文档问答带来源 |
| 5 | 会话摘要 + SSE 事件 | 多轮不爆 Token |
| 6～7 | RAG 作为 Agent 工具 + HITL | 退货故事三条用例 |
| 8 | MCP + Skill | 热插拔一个工具 |
| 9～10 | 精读 Ragent rag + agent | 源码笔记 + 一次小 PR 级改动 |
| 11 | 观测与限流（可抄思路） | 一次请求的 step 日志 |
| 12 | 自己的窄场景 Agent | README + 评测集 |

若只有 8 周：合并 3～4，压缩 9～10 为「只读 agent + 一个检索通道」。

---

## 6. 学习时怎么用 Ragent（避免两种极端）

**极端 A：当视频课从前往后看完。** 没有自己的闭环，面试一追问就空。  
**极端 B：第一天配生产依赖。** 两天都在打 Docker，还没见到 ReAct。

正确用法：

1. **对照**：你卡在「融合多路检索」时，再去看 Ragent 的 RRF 实现。
2. **抄结构不抄大而全**：ChatClient 接口、SearchChannel、ReAct maxSteps、确认态，这些抽象值得抄。
3. **用 Issue / 测试当习题**：仓库里 Agent、MCP 相关测试比 README 更接近真实约束。
4. **官网文档当词典**：意图树、入库节点、模型档位，用到再查。

---

## 7. 面试与自我检测题（定期自问）

理论：

1. RAG 和微调各解决什么问题？为什么企业内部知识更常上 RAG？
2. 为什么订单号检索不能只靠向量？混合检索如何融合？
3. Workflow 和 Agent 如何选型？给一个必须用 Agent 的例子、一个不该用的例子。
4. ReAct 如何防止死循环？工具返回很大时上下文怎么处理？
5. MCP 相比「代码里写死 HTTP 工具」的收益和代价？
6. 写操作为什么要 HITL？只靠 Prompt「你必须小心」为什么不够？

结合 Ragent：

7. `infra-ai` 和 `rag` 拆开的好处是什么？
8. v1 为什么还保留？全上 v2 的代价是什么（延迟、费用、不可复现）？
9. 检索单通道失败为什么不能 fail-fast 整次问答？
10. 你自己的项目比 Ragent 少了哪些生产特性？上线最小集你选哪三件？

---

## 8. 推荐阅读（短、够用）

- 官方：[Ragent 文档](https://nageoffer.com/ragent)（架构、快速启动、为何不用 Spring AI）
- MCP：[modelcontextprotocol.io](https://modelcontextprotocol.io) 概念页 + Java SDK 示例
- AgentScope Java 文档（Ragent `agent` 模块依赖 `io.agentscope`）
- 论文/博文：ReAct；一篇 RRF 检索融合；一篇「RAG 评测」综述即可
- 工程：Langfuse 文档里「Trace / Generation / Tool」模型，对照你怎么打点

---

## 9. 你现在就可以做的第一件事（今天）

1. 新建 Spring Boot 项目，只做第 1 阶段的 `ChatClient` + `/api/chat`。
2. 把本文第 2 阶段的 tool 循环画在纸上，标出 `maxIterations`。
3. Star / Watch [ragent](https://github.com/nageoffer/ragent)，但 **先不要以跑全套为本周 KPI**。

当你完成第 5 阶段三条用例时，再 clone Ragent，按第 7 阶段读 `agent` 模块——那时每一段「中间件、确认、记忆」都会对得上你踩过的坑。

---

## 附录 A：Ragent 技术栈速查

| 层面 | 选型（以仓库为主，文档 v1 段落可能滞后） |
| --- | --- |
| 后端 | Java 17、Spring Boot 4.x、MyBatis-Plus |
| Agent 引擎 | AgentScope 2.x |
| 协议 | MCP Java SDK |
| 前端 | React 18、Vite、TypeScript |
| 检索 | pgvector 或 Milvus；可选 ES、LightRAG/Neo4j、联网 |
| 中间件 | Redis/Redisson、RocketMQ、S3/OSS、Sa-Token |
| 文档 | Apache Tika |
| 观测 | 自研 Trace + Langfuse |

## 附录 B：常见误区（官方归纳 + 学习建议）

| 误区 | 正确做法 |
| --- | --- |
| 调通 Embedding + Chat 就算会 RAG | 补上解析、分块、改写、融合、引文、评测 |
| 接上 MCP 就算企业级 Agent | 补上校验、确认、权限、失败、观测、限流 |
| 只换更强模型 | 先看召回和工具选错率 |
| 上来就微调 | 应用层先 RAG + 工具；微调是数据与指标都稳定后的选项 |
| 20 轮对话全塞进上下文 | N 轮 + 摘要；Agent 还要裁剪 tool 输出 |
| 为了 Agent 而 Agent | 能 Workflow 解决的不要上 ReAct（更贵更慢更难测） |

---

*文档根据 GitHub `nageoffer/ragent` 公开 README、根 POM 与官网介绍整理，实现细节以你 clone 时的 `main` 分支为准。*
