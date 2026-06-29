# TV-01: MCP Java SDK 验证报告

## 1. SDK 信息

- **官方仓库**: https://github.com/modelcontextprotocol/java-sdk
- **描述**: The official Java SDK for Model Context Protocol servers and clients
- **维护者**: Model Context Protocol + Spring AI
- **Stars**: 3491+

## 2. Maven 依赖

```xml
<dependency>
    <groupId>io.modelcontextprotocol</groupId>
    <artifactId>mcp</artifactId>
    <version>0.5.0</version>
</dependency>
<!-- 或使用 Spring AI 集成 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 3. MCP Client 连接方式

### 方式 A: Stdio Transport (推荐用于本地进程)

```java
// 启动 MCP Server 作为子进程，通过 stdio 通信
McpClient client = McpClient.using(
    new StdioClientTransport(
        new ProcessBuilder("codegraph", "serve", "--mcp", "--path", "/path/to/project")
    )
).sync();

// 初始化连接
client.initialize();

// 调用工具
CallToolResult result = client.callTool(
    new CallToolRequest("codegraph_explore", Map.of("query", "OrderService"))
);
```

### 方式 B: SSE Transport (用于远程服务)

```java
// 通过 HTTP SSE 连接远程 MCP Server
McpClient client = McpClient.using(
    new SseClientTransport("http://localhost:8080/sse")
).sync();
```

## 4. 验证步骤

### Step 1: 创建验证项目

```bash
cd code-knowledge-graph/backend
# 添加 MCP 依赖到 pom.xml
```

### Step 2: 创建 MCP Client Service

```java
@Service
public class McpClientService {

    private McpSyncClient client;
    private Process mcpProcess;

    public void startConnection(Path projectPath) {
        // 1. 启动 codegraph MCP Server
        ProcessBuilder pb = new ProcessBuilder(
            "codegraph", "serve", "--mcp", "--path", projectPath.toString()
        );
        pb.redirectErrorStream(true);
        mcpProcess = pb.start();

        // 2. 创建 MCP Client
        client = McpClient.using(new StdioClientTransport(mcpProcess))
            .requestTimeout(Duration.ofSeconds(30))
            .capabilities(ClientCapabilities.builder()
                .roots(true)
                .sampling(false)
                .build())
            .sync();

        // 3. 初始化连接
        InitializeResult initResult = client.initialize();
        log.info("MCP Server initialized: {}", initResult.getServerInfo());
    }

    public String callTool(String toolName, Map<String, Object> args) {
        CallToolResult result = client.callTool(
            new CallToolRequest(toolName, args)
        );

        // 解析返回内容
        List<Content> contents = result.getContent();
        return contents.stream()
            .filter(c -> c.type() == ContentType.TEXT)
            .map(c -> ((TextContent) c).text())
            .collect(Collectors.joining("\n"));
    }

    public void close() {
        if (client != null) {
            client.closeGracefully();
        }
        if (mcpProcess != null && mcpProcess.isAlive()) {
            mcpProcess.destroyForcibly();
        }
    }
}
```

## 5. codegraph MCP 工具列表

根据当前环境的 MCP 配置，codegraph 提供以下工具：

| 工具名 | 功能 | 参数 |
|--------|------|------|
| `codegraph_files` | 获取索引文件列表 | format, path, pattern |
| `codegraph_explore` | 探索代码结构和关系 | query, maxFiles |
| `codegraph_search` | 搜索符号 | query, kind, limit |
| `codegraph_callers` | 获取调用者 | symbol, limit |
| `codegraph_callees` | 获取被调用者 | symbol, limit |
| `codegraph_impact` | 分析影响范围 | symbol, depth |
| `codegraph_node` | 获取节点详情 | symbol, includeCode |

## 6. 验证结果

### 测试场景

1. **启动连接**: 启动 codegraph MCP Server 并建立连接
2. **调用 codegraph_files**: 获取项目文件列表
3. **调用 codegraph_explore**: 探索代码结构
4. **解析 JSON 输出**: 验证输出格式可解析

### 验证代码

创建文件: `backend/src/test/java/com/example/ckg/mcp/McpClientTest.java`

```java
@SpringBootTest
public class McpClientTest {

    @Test
    void testMcpConnection() throws Exception {
        Path testProject = Paths.get("../test-sample-project");

        McpClientService service = new McpClientService();
        service.startConnection(testProject);

        // 测试获取文件列表
        String filesResult = service.callTool("codegraph_files", Map.of());
        assertNotNull(filesResult);
        assertTrue(filesResult.contains(".java"));

        // 测试探索代码
        String exploreResult = service.callTool("codegraph_explore",
            Map.of("query", "all classes", "maxFiles", 10));
        assertNotNull(exploreResult);

        // 验证 JSON 可解析
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(exploreResult);
        assertTrue(json.has("nodes") || json.has("files"));

        service.close();
    }
}
```

## 7. 验证结论

| 验证项 | 状态 | 说明 |
|--------|------|------|
| MCP Java SDK 可用性 | ✅ | 官方 SDK 存在且活跃维护 |
| Stdio Transport 支持 | ✅ | 支持子进程通信 |
| codegraph MCP 工具可调用 | ⏳ 待验证 | 需实际运行测试 |
| 输出格式可解析 | ⏳ 待验证 | 需实际运行测试 |

## 8. 备选方案

如果 MCP Java SDK 集成遇到问题，可考虑：

### 方案 A: 自建 Stdio 封装

```java
public class SimpleMcpClient {
    private Process process;
    private BufferedReader reader;
    private PrintWriter writer;

    public void start(Path projectPath) {
        ProcessBuilder pb = new ProcessBuilder(
            "codegraph", "serve", "--mcp", "--path", projectPath.toString()
        );
        process = pb.start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new PrintWriter(process.getOutputStream(), true);
    }

    public JsonNode callTool(String toolName, Map<String, Object> args) {
        // 构建 MCP 请求 JSON
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", UUID.randomUUID().toString(),
            "method", "tools/call",
            "params", Map.of("name", toolName, "arguments", args)
        );

        writer.println(toJson(request));
        String response = reader.readLine();
        return parseJson(response);
    }
}
```

### 方案 B: Python 中间层

使用 Python 调用 MCP，Java 通过进程调用 Python 脚本获取结果。

---

**验证状态**: 设计完成，待实际运行验证

**下一步**: 创建测试项目并运行实际验证