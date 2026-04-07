# RAG 检索 + 智能问答设计方案

**版本**: v1.0  
**日期**: 2026-03-10  
**状态**: 方案设计  
**适用范围**: 现有知识库文档分块与向量入库能力之上，补齐在线检索与智能问答能力

---

## 1. 目标与原则

### 1.1 目标

结合现有 RAG 入库链路（文档分块、向量化、ES 入库），设计一套可落地的在线问答方案：

1. 检索采用 **ES 双路混合召回**：`BM25 + 向量检索`
2. 融合采用 **RRF（Reciprocal Rank Fusion）**
3. 精排采用 **阿里 Rerank 模型**
4. 输出支持 **可追溯引用**（文档、分块、片段）
5. 与前端“智能问答”页面能力打通

### 1.2 设计原则

1. 尽量复用现有代码和数据结构
2. 优先保证可解释性和可回溯性
3. 失败可降级，服务可观测
4. 首版聚焦 MVP，避免一次性过度设计

---

## 2. 现状盘点（结合当前代码）

### 2.1 已有能力

1. 文档入库主链路已完成：  
`上传 -> MinIO -> 解析分块 -> MySQL 持久化 -> 向量生成 -> ES 索引`
2. ES 索引服务已支持：
   1. 自动建索引与 mapping
   2. 批量索引和按 `docId/indexId` 删除
   3. `hybridSearch(kbId, query, topK)` 召回接口
3. 管理端已有“召回测试”页面与接口：
   1. `POST /datasets/base/{kbId}/recall-test`
4. 前端已有智能问答页壳：
   1. 会话区、流式消息渲染、文件上传、知识库选择 UI

### 2.2 当前缺口

1. 当前混合检索是 `script_score` 单次融合，不是“分路召回 + RRF 融合”
2. 未接入 rerank 阶段
3. `/api/chat` 仍是通用聊天逻辑，未形成知识库问答编排闭环
4. 前端智能问答的知识库列表/会话列表存在 mock 或硬编码
5. 引用证据（来源分块）未作为一等结构返回给前端

---

## 3. 总体方案

### 3.1 端到端流程

```text
用户问题
  -> Query 预处理
  -> 并行召回
     -> BM25 召回 TopN_bm25
     -> 向量召回 TopN_vec
  -> RRF 融合
  -> Rerank 精排（阿里）
  -> 上下文组装（去重/截断/多文档平衡）
  -> LLM 生成答案（带引用）
  -> SSE 流式返回前端
```

### 3.2 逻辑分层

1. **Data Layer**: 继续复用 `knowledge_document + document_chunk + ES 索引`
2. **Retrieval Layer**:
   1. `Bm25Retriever`
   2. `VectorRetriever`
   3. `RrfFusionService`
   4. `RerankService`
3. **QA Orchestrator**:
   1. 检索编排
   2. Prompt 构造
   3. 流式答案输出
4. **API Layer**:
   1. 召回调试接口
   2. 智能问答 SSE 接口
5. **Frontend Layer**:
   1. 智能问答面板
   2. 引用证据与调试信息面板

---

## 4. 检索策略设计（BM25 + 向量 + RRF + Rerank）

### 4.1 Query 预处理

1. 标准化：去首尾空白、连续空格归一
2. 防抖：长度过短（如 < 2 字）直接提示补充问题
3. 可选（非首版强制）：query rewrite（拼写纠错、同义扩展）

### 4.2 BM25 召回

建议字段与权重：

1. `content`（主字段，boost 高）
2. `headings`（次高）
3. `documentName`（中）
4. `keywords`（中）

建议参数：

1. `bm25TopK = 40`（首版可配置）
2. 过滤条件：`kbId == 当前知识库`，必要时叠加 `docId/fileType/path` 过滤

### 4.3 向量召回

1. 使用现有 embedding 模型生成 query 向量
2. 在 ES `dense_vector` 字段执行 kNN 检索
3. 建议参数：
   1. `vectorTopK = 40`
   2. `numCandidates = 100`（按 ES 版本能力调整）

### 4.4 RRF 融合

对 BM25 和向量两路结果按排名融合：

\[
RRF(d) = \sum_{r \in rankers} \frac{1}{k + rank_r(d)}
\]

建议参数：

1. `k = 60`
2. 融合后取 `rrfTopK = 30`
3. 主键按 `indexId` 去重

RRF 价值：

1. 避免不同检索分值尺度不可直接比较
2. 同时保留关键词命中与语义相似的优势

### 4.5 Rerank 精排（阿里模型）

1. 输入：`(query, candidateChunkText)` 对
2. 目标：在 RRF 候选内做语义相关性精排
3. 模型：阿里 DashScope rerank（模型名配置化，如 `gte-rerank-v2`）
4. 建议参数：
   1. `rerankInputTopK = 30`
   2. `finalTopN = 8~12`
5. 失败降级：
   1. rerank 超时/失败时，降级为 RRF TopN
   2. 标记 `degraded=true` 并记录日志

### 4.6 上下文组装策略

1. 多样性约束：同一文档最多取 `maxPerDoc`（如 3）
2. 片段截断：超长 chunk 做安全截断（保留头尾）
3. 上下文预算：按模型 token 限制控制（例如 4k~8k token）
4. 输出结构包含：
   1. `indexId/chunkId/docId`
   2. `contentSnippet`
   3. `scoreBreakdown`（bm25Rank/vectorRank/rrf/rerank）

---

## 5. 智能问答编排设计

### 5.1 生成提示词（核心约束）

System 约束建议：

1. 仅基于提供证据回答，不得编造来源
2. 证据不足时明确说“未在知识库中找到充分依据”
3. 回答后附引用编号（如 `[1][2]`）

### 5.2 输出结构

建议统一为结构化对象（流式可以分事件返回）：

```json
{
  "answer": "......",
  "citations": [
    {
      "ref": 1,
      "docId": 123,
      "indexId": "sec-1-2",
      "fileName": "xxx.docx",
      "snippet": "......"
    }
  ],
  "retrievalDebug": {
    "degraded": false,
    "latencyMs": {
      "bm25": 45,
      "vector": 62,
      "rrf": 2,
      "rerank": 110,
      "llm": 980
    }
  }
}
```

### 5.3 失败与降级策略

1. ES 不可用：直接返回“知识库检索不可用”错误
2. embedding 失败：提示稍后重试，不进入问答
3. rerank 失败：降级 RRF 直出
4. LLM 失败：保留已检索证据，返回可重试提示

---

## 6. 后端接口设计

### 6.1 召回调试接口（建议升级）

可在现有 `recall-test` 基础上扩展调试信息，或新增：

`POST /datasets/base/{kbId}/retrieve-debug`

请求：

```json
{
  "query": "变压器额定容量要求是什么",
  "topK": 10,
  "showDebug": true
}
```

响应（示意）：

```json
{
  "items": [
    {
      "indexId": "sec-1",
      "docId": 101,
      "chunkId": 8001,
      "content": "...",
      "scores": {
        "bm25Rank": 3,
        "vectorRank": 1,
        "rrf": 0.0317,
        "rerank": 0.892
      }
    }
  ]
}
```

### 6.2 智能问答接口（新增，推荐）

`POST /datasets/base/{kbId}/qa/stream`（SSE）

请求：

```json
{
  "sessionId": "sid-xxx",
  "message": "对于Um=72.5kV时容量要求是什么？",
  "topK": 8
}
```

SSE 事件建议：

1. `retrieval`: 检索开始/完成，返回候选摘要
2. `citation`: 返回引用证据（可多次）
3. `message`: 模型文本增量
4. `done`: 完成
5. `error`: 失败

### 6.3 与现有 `/api/chat` 的关系

推荐方案：**知识问答走独立接口**，不直接复用通用 `/api/chat`。

原因：

1. 职责更清晰：通用聊天 vs 知识检索问答
2. 便于隔离权限、超时、日志与指标
3. 对前端智能问答页的演进更稳定

---

## 7. 前端“智能问答”页功能点设计

结合当前 `FrontIntelligentChatPanel + FrontChatWorkspace`，建议增量如下。

### 7.1 首版必做

1. **知识库真实列表接入**
   1. 由接口拉取而非硬编码
2. **按知识库发起问答**
   1. 选定 `kbId` 后调用 `/qa/stream`
3. **流式回答 + 引用展示**
   1. 在回答底部展示引用卡片（文件名、片段、定位）
4. **会话持久化（基础版）**
   1. 至少保留本地 session 与最近会话列表
5. **错误态与降级态提示**
   1. 例如“rerank 降级中，结果可能较粗略”

### 7.2 增强功能（第二阶段）

1. 检索调试抽屉：
   1. 展示 BM25/向量/RRF/rerank 各阶段结果
2. 证据高亮：
   1. 高亮回答中与引用卡片的映射
3. 会话后端持久化与历史恢复

### 7.3 交互流程

1. 用户选择知识库
2. 输入问题并发送
3. UI 显示“检索中”
4. 返回证据卡片（可先于答案）
5. 流式生成答案
6. 完成后可展开“检索调试”

---

## 8. 配置与参数建议

```yaml
app:
  rag:
    retrieval:
      bm25-topk: 40
      vector-topk: 40
      rrf-k: 60
      rrf-topk: 30
      final-topn: 8
      timeout-ms: 1500
    rerank:
      enabled: true
      provider: dashscope
      base-url: ${app.chat.base-url}
      api-key: change-me-api-key
      model: gte-rerank-v2
      path: /v1/rerank
      timeout-ms: 1200
      fallback-rrf: true
```

说明：

1. 模型名需配置化，避免硬编码
2. timeout 与 topK 需支持环境差异化调参

---

## 9. 监控与评测

### 9.1 线上指标

1. 检索阶段耗时（bm25/vector/rrf/rerank）
2. 问答端到端耗时（P50/P95）
3. 降级率（rerank 降级占比）
4. 引用覆盖率（回答含引用比例）

### 9.2 质量指标（离线）

1. Recall@K
2. MRR / nDCG
3. 引用准确率（人工抽样）
4. 幻觉率（回答无依据占比）

---

## 10. 分阶段落地计划

### Phase 1（检索能力升级）

1. 现有 `hybridSearch` 拆分为双路召回
2. 增加 RRF 融合与调试数据结构
3. 管理端召回测试页面显示多阶段分数

### Phase 2（问答闭环）

1. 新增 `/qa/stream` 接口
2. 接入阿里 rerank 与失败降级
3. 输出结构化引用并流式返回

### Phase 3（前端智能问答增强）

1. 智能问答页接入真实知识库列表
2. 展示引用证据、降级提示、调试抽屉
3. 会话持久化与历史恢复

---

## 11. MVP 范围与非目标

### 11.1 MVP 范围

1. 单知识库问答
2. BM25 + 向量 + RRF + 阿里 rerank
3. 流式回答 + 引用展示
4. 可观测与降级

### 11.2 非目标

1. 跨知识库路由
2. 复杂 Agent 多跳规划
3. 自动评测平台与 A/B 系统

---

## 12. 验收标准

1. 检索链路可稳定产出：
   1. BM25 结果
   2. 向量结果
   3. RRF 融合结果
   4. rerank 最终结果
2. 智能问答答案可附带可点击引用
3. rerank 失败可自动降级且前端可感知
4. 关键指标可观测（耗时、失败率、降级率）
5. 与现有分块入库链路兼容，无需重做数据生产

---

## 13. 附：检索编排伪代码

```java
List<Candidate> bm25 = bm25Retriever.search(kbId, query, bm25TopK);
List<Candidate> vector = vectorRetriever.search(kbId, query, vectorTopK);

List<Candidate> fused = rrfFusion.fuse(
    List.of(
      RankedList.of("bm25", bm25),
      RankedList.of("vector", vector)
    ),
    rrfK,
    rrfTopK
);

List<Candidate> reranked;
try {
    reranked = rerankService.rerank(query, fused, finalTopN);
} catch (Exception ex) {
    reranked = fused.subList(0, Math.min(finalTopN, fused.size()));
    markDegraded("rerank_failed");
}

Answer answer = qaGenerator.generate(query, reranked);
return answer;
```
