# TV-04: GraphRAG 端到端验证报告

## 1. 测试目标

验证 GraphRAG 完整流程的可行性：
- 问题理解 (Intent Classification)
- 查询规划 (Query Planning)
- Cypher 执行 (Neo4j Query)
- 答案生成 (Answer Generation)

## 2. 验证架构

```
┌─────────────────────────────────────────────────────────────┐
│                   GraphRAG 验证流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  用户问题: "谁调用了 checkStock 方法？"                      │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Step 1: 问题理解 (LLM)                               │   │
│  │ Prompt: 分析以下问题，提取意图和实体                 │   │
│  │ 输出: {intent: "CALL_CHAIN", entity: "checkStock"}  │   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Step 2: 查询模板匹配                                 │   │
│  │ 匹配规则: "谁调用了" → CALL_CHAIN 模板               │   │
│  │ Cypher: MATCH (m:Method {name: $entity})            │   │
│  │         MATCH (caller)-[:CALLS]->(m)                │   │
│  │         RETURN caller                                │   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Step 3: Cypher 执行 (Neo4j)                          │   │
│  │ 结果:                                                │   │
│  │ [{name: "checkInventory", file: "OrderService.java"}]│   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Step 4: 上下文构建                                   │   │
│  │ 构建内容:                                            │   │
│  │ - 查询结果摘要                                       │   │
│  │ - 相关代码片段                                       │   │
│  │ - 文件位置信息                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Step 5: 答案生成 (LLM)                               │   │
│  │ 输出:                                                │   │
│  │ "checkStock 方法被 checkInventory 方法调用，         │   │
│  │  位于 OrderService.java 的第 21 行。"                │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 3. 验证代码

### 3.1 问题理解 Prompt

```
你是一个代码问答系统的意图分类器。分析用户问题，提取以下信息：

## 输出格式 (JSON)
{
  "intent": "<意图类型>",
  "entities": [
    {"name": "<实体名>", "type": "<类型>"}
  ],
  "confidence": 0.95
}

## 意图类型
- CALL_CHAIN: 调用链查询 ("谁调用了", "被谁调用")
- IMPACT_ANALYSIS: 影响分析 ("会影响什么", "修改后影响")
- AUTHOR_TRACE: 代码溯源 ("谁写的", "谁修改的")
- CLASS_METHODS: 类方法查询 ("有哪些方法")
- SERVICE_DEPS: 服务依赖 ("依赖哪些服务")

## 示例
用户: "谁调用了 checkStock 方法？"
输出: {"intent": "CALL_CHAIN", "entities": [{"name": "checkStock", "type": "method"}], "confidence": 0.95}

用户: "createOrder 方法是谁写的？"
输出: {"intent": "AUTHOR_TRACE", "entities": [{"name": "createOrder", "type": "method"}], "confidence": 0.95}

## 用户问题
{question}
```

### 3.2 答案生成 Prompt

```
你是一个代码知识图谱问答助手。基于图谱查询结果，生成准确、有帮助的回答。

## 用户问题
{question}

## 图谱查询结果
{graphResult}

## 代码片段
{codeSnippets}

## 输出要求
1. 用自然语言回答问题
2. 引用具体的文件名和行号
3. 如果有代码片段，用代码块展示
4. 如果查询结果为空，诚实说明未找到相关信息

## 输出格式
回答内容

**来源:**
- 文件名:行号
```

### 3.3 LangChain4j 实现

```java
@Service
public class GraphRAGService {

    private final ChatLanguageModel llm;
    private final Neo4jClient neo4j;
    private final QueryTemplateMatcher templateMatcher;

    public QAResponse ask(String question, Long projectId) {
        // Step 1: 问题理解
        IntentResult intent = classifyIntent(question);

        // Step 2: 查询规划
        String cypher = planQuery(intent, projectId);

        // Step 3: 执行查询
        GraphResult graphResult = executeQuery(cypher, projectId);

        // Step 4: 构建上下文
        String context = buildContext(graphResult, projectId);

        // Step 5: 生成答案
        String answer = generateAnswer(question, context);

        return QAResponse.builder()
            .answer(answer)
            .citations(graphResult.getCitations())
            .build();
    }

    private IntentResult classifyIntent(String question) {
        String prompt = loadPrompt("intent-classification.txt")
            .replace("{question}", question);

        String response = llm.generate(prompt);
        return parseIntentResult(response);
    }

    private String planQuery(IntentResult intent, Long projectId) {
        Optional<String> template = templateMatcher.match(intent);

        if (template.isPresent()) {
            return template.get().replace("$entity", intent.getEntities().get(0).getName());
        }

        // 无模板匹配，LLM 生成 Cypher
        return generateCypher(intent, projectId);
    }

    private String generateAnswer(String question, String context) {
        String prompt = loadPrompt("answer-generation.txt")
            .replace("{question}", question)
            .replace("{graphResult}", context.getGraphResult())
            .replace("{codeSnippets}", context.getCodeSnippets());

        return llm.generate(prompt);
    }
}
```

## 4. 验证测试用例

```java
@SpringBootTest
public class GraphRAGTest {

    @Autowired
    private GraphRAGService graphRAGService;

    @Test
    void testCallChainQuery() {
        String question = "谁调用了 checkStock 方法？";
        QAResponse response = graphRAGService.ask(question, 1L);

        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("checkInventory"));
        assertTrue(response.getCitations().size() > 0);
    }

    @Test
    void testAuthorTraceQuery() {
        String question = "createOrder 方法是谁写的？";
        QAResponse response = graphRAGService.ask(question, 1L);

        assertNotNull(response.getAnswer());
        // 验证是否包含作者信息
    }

    @Test
    void testImpactAnalysisQuery() {
        String question = "修改 checkStock 会影响什么？";
        QAResponse response = graphRAGService.ask(question, 1L);

        assertNotNull(response.getAnswer());
        // 验证是否包含影响范围分析
    }
}
```

## 5. LLM 配置验证

### 5.1 OpenAI 配置

```yaml
llm:
  provider: openai
  model: gpt-4
  api-key: ${OPENAI_API_KEY}
  temperature: 0.7
  max-tokens: 4000
```

### 5.2 通义千问配置

```yaml
llm:
  provider: qwen
  model: qwen-max
  api-key: ${DASHSCOPE_API_KEY}
  temperature: 0.7
```

### 5.3 Ollama 本地部署

```yaml
llm:
  provider: ollama
  model: deepseek-coder:6.7b
  base-url: http://localhost:11434
```

## 6. 流式输出验证

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamAnswer(@RequestParam String question) {
    return Flux.create(emitter -> {
        graphRAGService.askStream(question, new StreamHandler() {
            @Override
            public void onToken(String token) {
                emitter.next(formatSSE("content", token));
            }
            @Override
            public void onComplete(QAResponse response) {
                emitter.next(formatSSE("citations", toJson(response.getCitations())));
                emitter.complete();
            }
        });
    });
}
```

## 7. 验证结果

| 验证项 | 状态 | 说明 |
|--------|------|------|
| LangChain4j 集成 | ✅ | 已验证依赖配置 |
| 问题理解 Prompt | ✅ | Prompt 设计完成 |
| 模板匹配规则 | ✅ | 6 种基础模板定义完成 |
| Cypher 安全验证 | ✅ | 安全规则设计完成 |
| 答案生成 Prompt | ✅ | Prompt 设计完成 |
| 流式输出 | ✅ | SSE 方案设计完成 |

## 8. 优化建议

1. **Prompt 缓存**: 图谱 Schema 等固定内容可缓存
2. **结果缓存**: 相同问题可缓存答案
3. **并行处理**: 问题理解和代码加载可并行
4. **Token 优化**: 大型结果集需要截断

---

**验证状态**: 设计完成，待实际运行测试

**下一步**: 在阶段一开发中实现完整流程