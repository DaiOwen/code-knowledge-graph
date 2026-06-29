# TV-02: codegraph 输出格式验证报告

## 1. 测试环境

- **测试项目**: test-sample-project (5 个 Java 文件)
- **codegraph 版本**: 当前环境
- **索引结果**: 60 nodes, 90 edges

## 2. codegraph MCP 工具输出格式

### 2.1 codegraph_files

**用途**: 获取索引文件列表

**输出格式**: Markdown 文本

```
## Files (5)

- src/main/java/com/example/InventoryService.java (java, 6 symbols)
- src/main/java/com/example/Order.java (java, 19 symbols)
- src/main/java/com/example/OrderItem.java (java, 15 symbols)
- src/main/java/com/example/OrderRequest.java (java, 10 symbols)
- src/main/java/com/example/OrderService.java (java, 10 symbols)
```

**解析策略**: 使用正则表达式提取文件路径和符号数量

```java
Pattern pattern = Pattern.compile("- (.+) \\((\\w+), (\\d+) symbols\\)");
```

### 2.2 codegraph_explore

**用途**: 探索代码结构和调用关系

**输出格式**: Markdown 文本，包含：
- 调用链路径 (Flow)
- 依赖分析 (Blast radius)
- 源代码块 (Source Code)

**关键信息提取**:

```
## Flow (call path among the symbols you queried)

1. createOrder (src/main/java/com/example/OrderService.java:8)
   ↓ calls
2. checkInventory (src/main/java/com/example/OrderService.java:21)
   ↓ calls
3. checkStock (src/main/java/com/example/InventoryService.java:7)
```

**解析策略**:
1. 解析 Flow 部分 → 构建调用链
2. 解析 Blast radius → 识别依赖关系
3. 解析 Source Code → 提取代码片段

### 2.3 codegraph_search

**用途**: 搜索符号

**输出格式**: Markdown 文本

```
## Search Results (1 found)

### createOrder (method)
src/main/java/com/example/OrderService.java:8
`Order (OrderRequest request)`
```

**解析策略**: 正则表达式提取符号名、类型、位置、签名

### 2.4 codegraph_callers

**用途**: 获取调用者

**输出格式**:

```
## Callers of checkStock (1 found)

- checkInventory (method) - src/main/java/com/example/OrderService.java:21
```

### 2.5 codegraph_callees

**用途**: 获取被调用者

**输出格式**:

```
## Callees of createOrder (5 found)

- validateOrder (method) - src/main/java/com/example/OrderService.java:15
- checkInventory (method) - src/main/java/com/example/OrderService.java:21
- buildOrder (method) - src/main/java/com/example/OrderService.java:27
- OrderRequest (class) - src/main/java/com/example/OrderRequest.java:5
- Order (class) - src/main/java/com/example/Order.java:5
```

### 2.6 codegraph_node

**用途**: 获取节点详情

**输出格式**:

```
## OrderService (class)

**Location:** src/main/java/com/example/OrderService.java:3

**Members (7):**

- orderRepository (field):5 — `OrderRepository orderRepository`
- inventoryService (field):6 — `InventoryService inventoryService`
- createOrder (method):8 — `Order (OrderRequest request)`
...
```

### 2.7 codegraph_impact

**用途**: 分析影响范围

**输出格式**: 类似 callers，但包含多级依赖

## 3. 数据结构映射

### 3.1 输出 → Neo4j 节点映射

| codegraph 输出 | Neo4j 节点 | 属性 |
|----------------|-----------|------|
| `class` | `Class` | name, fullName, filePath, startLine, type=CLASS |
| `method` | `Method` | name, signature, filePath, startLine, returnType |
| `field` | `Field` | name, type, filePath, startLine |

### 3.2 输出 → Neo4j 关系映射

| codegraph 工具 | 关系类型 | 说明 |
|----------------|---------|------|
| callees | CALLS | Method → Method |
| node Members | HAS_METHOD | Class → Method |
| node Members | HAS_FIELD | Class → Field |

## 4. 解析器实现

```java
@Component
public class CodeGraphOutputParser {

    // 解析 search 结果
    public List<SymbolInfo> parseSearchResult(String output) {
        List<SymbolInfo> symbols = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "### (\\w+) \\((\\w+)\\)\\n" +
            "([^\\n]+):(\\d+)\\n" +
            "`([^`]+)`"
        );
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            symbols.add(SymbolInfo.builder()
                .name(matcher.group(1))
                .type(matcher.group(2))
                .filePath(matcher.group(3).trim())
                .line(Integer.parseInt(matcher.group(4)))
                .signature(matcher.group(5))
                .build());
        }
        return symbols;
    }

    // 解析 callees 结果
    public List<CallRelation> parseCalleesResult(String output, String callerName) {
        List<CallRelation> relations = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "- (\\w+) \\((\\w+)\\) - ([^:]+):(\\d+)"
        );
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            relations.add(CallRelation.builder()
                .callerName(callerName)
                .calleeName(matcher.group(1))
                .calleeType(matcher.group(2))
                .calleeFile(matcher.group(3).trim())
                .calleeLine(Integer.parseInt(matcher.group(4)))
                .build());
        }
        return relations;
    }

    // 解析 Flow 调用链
    public List<CallChainStep> parseFlowResult(String output) {
        List<CallChainStep> steps = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "(\\d+)\\. (\\w+) \\(([^:]+):(\\d+)\\)"
        );
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            steps.add(CallChainStep.builder()
                .order(Integer.parseInt(matcher.group(1)))
                .symbolName(matcher.group(2))
                .filePath(matcher.group(3))
                .lineNumber(Integer.parseInt(matcher.group(4)))
                .build());
        }
        return steps;
    }
}
```

## 5. 验证结论

| 验证项 | 状态 | 说明 |
|--------|------|------|
| 输出格式可解析 | ✅ | Markdown 格式，可用正则解析 |
| 包含符号名称 | ✅ | 所有工具返回符号名 |
| 包含文件路径 | ✅ | 所有工具返回文件路径 |
| 包含行号 | ✅ | 所有工具返回行号 |
| 包含方法签名 | ✅ | search/node 返回签名 |
| 包含调用关系 | ✅ | callers/callees/explore 返回调用关系 |
| 包含完整代码 | ⚠️ | explore 返回代码，但需配置 includeCode |

## 6. 注意事项

1. **输出格式是 Markdown 文本**，不是 JSON，需要编写解析器
2. **codegraph 需要先初始化** (`codegraph init`)
3. **行号从 1 开始**，与 Neo4j 存储保持一致
4. **文件路径是相对路径**，需要结合项目根路径

## 7. 转换为 Neo4j Cypher 示例

```cypher
// 创建 Class 节点
CREATE (c:Class {
    name: 'OrderService',
    fullName: 'com.example.OrderService',
    filePath: 'src/main/java/com/example/OrderService.java',
    startLine: 3
})

// 创建 Method 节点
CREATE (m:Method {
    name: 'createOrder',
    signature: 'Order (OrderRequest request)',
    filePath: 'src/main/java/com/example/OrderService.java',
    startLine: 8,
    returnType: 'Order'
})

// 创建关系
CREATE (c)-[:HAS_METHOD]->(m)
CREATE (m)-[:CALLS]->(:Method {name: 'validateOrder', startLine: 15})
CREATE (m)-[:CALLS]->(:Method {name: 'checkInventory', startLine: 21})
```

---

**验证状态**: ✅ 完成

**结论**: codegraph MCP 输出格式可用正则表达式解析，包含构建知识图谱所需的所有信息