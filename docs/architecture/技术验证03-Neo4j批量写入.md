# TV-03: Neo4j 批量写入性能验证报告

## 1. 测试目标

验证 Neo4j 批量写入性能，确保能处理大型项目解析场景：
- 目标: 10 万节点 + 50 万关系，写入时间 < 5 分钟
- 验证索引对写入性能的影响

## 2. 测试方案

### 2.1 Docker 启动 Neo4j

```bash
# 启动 Neo4j
docker-compose up -d neo4j

# 等待 Neo4j 就绪
curl -f http://localhost:7474
```

### 2.2 批量写入策略

#### 方式 A: UNWIND 批量插入 (推荐)

```cypher
// 批量创建 Method 节点
UNWIND $methods AS method
CREATE (m:Method {
    projectId: method.projectId,
    name: method.name,
    signature: method.signature,
    filePath: method.filePath,
    startLine: method.startLine,
    returnType: method.returnType
})
```

#### 方式 B: 使用 Neo4j Java Driver 批量事务

```java
@Service
public class Neo4jBatchWriter {

    private final Neo4jClient client;
    private static final int BATCH_SIZE = 1000;

    public void writeNodesBatch(List<NodeData> nodes) {
        List<List<NodeData>> batches = partition(nodes, BATCH_SIZE);

        for (List<NodeData> batch : batches) {
            client.query("""
                UNWIND $nodes AS node
                MERGE (m:Method {
                    projectId: node.projectId,
                    filePath: node.filePath,
                    name: node.name,
                    startLine: node.startLine
                })
                SET m.signature = node.signature,
                    m.returnType = node.returnType
                """)
            .bind(batch).to("nodes")
            .run();
        }
    }

    public void writeEdgesBatch(List<EdgeData> edges) {
        List<List<EdgeData>> batches = partition(edges, BATCH_SIZE);

        for (List<EdgeData> batch : batches) {
            client.query("""
                UNWIND $edges AS edge
                MATCH (from:Method {
                    projectId: edge.fromProjectId,
                    filePath: edge.fromFilePath,
                    name: edge.fromName,
                    startLine: edge.fromLine
                })
                MATCH (to:Method {
                    projectId: edge.toProjectId,
                    filePath: edge.toFilePath,
                    name: edge.toName,
                    startLine: edge.toLine
                })
                MERGE (from)-[r:CALLS]->(to)
                """)
            .bind(batch).to("edges")
            .run();
        }
    }
}
```

## 3. 性能测试代码

```java
@SpringBootTest
public class Neo4jPerformanceTest {

    @Autowired
    private Neo4jBatchWriter writer;

    @Test
    void testBatchWritePerformance() {
        // 生成 10 万测试节点
        List<NodeData> nodes = generateTestNodes(100000);
        List<EdgeData> edges = generateTestEdges(500000);

        // 测试节点写入
        long nodeStart = System.currentTimeMillis();
        writer.writeNodesBatch(nodes);
        long nodeTime = System.currentTimeMillis() - nodeStart;

        // 测试关系写入
        long edgeStart = System.currentTimeMillis();
        writer.writeEdgesBatch(edges);
        long edgeTime = System.currentTimeMillis() - edgeStart;

        System.out.println("节点写入: " + nodeTime + "ms");
        System.out.println("关系写入: " + edgeTime + "ms");
        System.out.println("总计: " + (nodeTime + edgeTime) + "ms");

        // 验证
        assertTrue(nodeTime + edgeTime < 300000, "总时间应 < 5 分钟");
    }

    private List<NodeData> generateTestNodes(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> NodeData.builder()
                .projectId(1L)
                .name("method_" + i)
                .signature("void method_" + i + "()")
                .filePath("src/Test_" + (i / 1000) + ".java")
                .startLine(i % 100 + 1)
                .returnType("void")
                .build())
            .collect(Collectors.toList());
    }

    private List<EdgeData> generateTestEdges(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> EdgeData.builder()
                .fromProjectId(1L)
                .fromFilePath("src/Test_" + ((i / 5) / 1000) + ".java")
                .fromName("method_" + (i / 5))
                .fromLine(i % 100 + 1)
                .toProjectId(1L)
                .toFilePath("src/Test_" + ((i % 5) / 1000) + ".java")
                .toName("method_" + (i % 100000))
                .toLine(i % 100 + 1)
                .build())
            .collect(Collectors.toList());
    }
}
```

## 4. 性能优化建议

### 4.1 索引策略

| 策略 | 说明 | 影响 |
|------|------|------|
| 写入前禁用索引 | 批量写入后重建 | 大批量时可能更快 |
| 使用 MERGE | 幂等写入，避免重复 | 比 CREATE 略慢 |
| 使用唯一约束 | 替代索引，更快查找 | 写入时需检查唯一性 |

### 4.2 批次大小调优

```java
// 根据数据大小调整批次
int batchSize = nodes.size() > 50000 ? 500 : 1000;
```

### 4.3 事务配置

```java
@Transactional
public void writeBatch(List<NodeData> batch) {
    // 单批事务，失败不影响其他批次
}
```

## 5. 预期性能基准

| 数据规模 | 预期时间 | 配置 |
|---------|---------|------|
| 1 万节点 + 5 万关系 | < 30s | 单事务 |
| 10 万节点 + 50 万关系 | < 5min | 分批事务 |
| 50 万节点 + 200 万关系 | < 20min | 分批 + 禁用索引重建 |

## 6. Neo4j 配置优化

```yaml
# docker-compose.yml
neo4j:
  environment:
    - NEO4J_dbms_memory_heap_initial__size=2G
    - NEO4J_dbms_memory_heap_max__size=4G
    - NEO4J_dbms_memory_pagecache_size=2G
```

## 7. 实际测试执行

### Step 1: 启动 Neo4j

```bash
cd code-knowledge-graph
docker-compose up -d neo4j
```

### Step 2: 运行性能测试

```bash
cd backend
mvn test -Dtest=Neo4jPerformanceTest
```

## 8. 验证结论

| 验证项 | 状态 | 说明 |
|--------|------|------|
| UNWIND 批量插入可用 | ✅ | Neo4j 标准方式 |
| 分批事务处理可行 | ✅ | Spring Data Neo4j 支持 |
| MERGE 幂等写入可行 | ✅ | 避免重复节点 |
| 性能预期合理 | ⏳ | 需实际运行测试 |

---

**验证状态**: 设计完成，待实际运行测试

**建议**: 在阶段一开发完成后，使用真实项目数据进行性能验证