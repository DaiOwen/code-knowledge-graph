# 后续开发计划

> 创建日期: 2026-06-24
> 项目: 代码知识图谱问答系统
> 当前阶段: 阶段一 MVP 核心功能 (60% 完成)

---

## 一、里程碑总览

| 里程碑 | 状态 | 预计周期 | 完成度 |
|--------|------|----------|--------|
| Phase 0: Technical Validation | ✅ 已完成 | 3-4 天 | 100% |
| Phase 1: MVP Core Features | 🔄 进行中 | 4-5 周 | 60% |
| Phase 2: UX Enhancement | ⏳ 待开始 | 2-3 周 | 0% |
| Phase 3-A: Automation Sync | ⏳ 待开始 | 1-2 周 | 0% |
| Phase 3-B: Business Semantic | ⏳ 待开始 | 1-2 周 | 0% |
| Phase 4: Production Ready | ⏳ 待开始 | 1-2 周 | 0% |

**总预计周期**: 9-13 周

---

## 二、当前阶段：Phase 1 MVP 核心功能

### 2.1 剩余工作清单

#### P0 - 关键路径 (必须完成)

| 编号 | 任务 | 模块 | 预计工时 | 说明 |
|------|------|------|----------|------|
| P0-01 | `parseNodes()` 实现 | 后端 | 4h | 解析 codegraph explore 输出的节点数据 |
| P0-02 | `parseEdges()` 实现 | 后端 | 4h | 解析 codegraph 调用关系边数据 |
| P0-03 | GraphRAG 端到端测试 | 后端 | 8h | 完整问答流程测试与调试 |
| P0-04 | 前后端 API 联调 | 全栈 | 8h | 确保所有 API 正常工作 |

**P0 总工时**: 约 24 小时 (3 个工作日)

#### P1 - 高优先级 (建议完成)

| 编号 | 任务 | 模块 | 预计工时 | 说明 |
|------|------|------|----------|------|
| P1-01 | D3.js 图谱可视化 | 前端 | 16h | Graph.vue 完整实现 |
| P1-02 | Monaco Editor 集成 | 前端 | 12h | 代码查看器 + 大文件优化 |
| P1-03 | SSE 流式输出 | 后端 | 8h | 问答流式响应 |
| P1-04 | 多 LLM 提供者测试 | 后端 | 8h | OpenAI/通义/Ollama 验证 |

**P1 总工时**: 约 44 小时 (5-6 个工作日)

#### P2 - 中优先级 (时间允许时完成)

| 编号 | 任务 | 模块 | 预计工时 | 说明 |
|------|------|------|----------|------|
| P2-01 | 单元测试覆盖 | 后端 | 16h | 核心服务测试 |
| P2-02 | API 文档 | 文档 | 4h | Swagger/OpenAPI |
| P2-03 | 错误处理优化 | 全栈 | 8h | 友好错误提示 |

**P2 总工时**: 约 28 小时 (3-4 个工作日)

### 2.2 Phase 1 详细开发计划

#### Week 1: 核心解析功能

```
Day 1-2: parseNodes/parseEdges 实现
├── 分析 codegraph explore 输出格式
├── 实现节点解析逻辑 (Class/Method/Field)
├── 实现边解析逻辑 (CALLS/EXTENDS/IMPLEMENTS)
└── 单元测试

Day 3: GraphRAG 端到端测试
├── 启动 Neo4j + 后端服务
├── 导入测试项目到 Neo4j
├── 测试问答流程
└── 修复发现的问题

Day 4-5: 前后端联调
├── 配置 CORS
├── 测试所有 API 端点
├── 修复接口问题
└── 完善错误处理
```

#### Week 2: 前端完善

```
Day 1-2: D3.js 图谱可视化
├── 安装 d3 依赖
├── 实现力导向图布局
├── 节点拖拽/缩放交互
└── 调用链路径高亮

Day 3-4: Monaco Editor 集成
├── 安装 monaco-editor
├── 实现代码高亮显示
├── 行号 + 代码定位
└── 大文件优化配置

Day 5: 联调测试
├── 完整用户流程测试
├── Bug 修复
└── 代码清理
```

#### Week 3: LLM 集成与测试

```
Day 1-2: SSE 流式输出
├── 后端 SSE 控制器实现
├── 前端 EventSource 接收
└── 流式渲染优化

Day 3-4: 多 LLM 测试
├── OpenAI 接口测试
├── 通义千问接口测试
├── Ollama 本地模型测试
└── 配置切换机制验证

Day 5: 集成测试
├── 端到端测试
├── 性能测试
└── 问题修复
```

---

## 三、Phase 2: UX Enhancement (体验增强)

### 3.1 功能列表

| 功能 | 优先级 | 预计工时 | 说明 |
|------|--------|----------|------|
| 图谱可视化增强 | P1 | 16h | 筛选、搜索、导出 |
| 代码查看器增强 | P1 | 12h | 文件树、历史版本 |
| 对话历史持久化 | P1 | 8h | 保存/加载会话 |
| 来源引用跳转 | P1 | 8h | 点击跳转到代码位置 |
| 流式输出优化 | P2 | 4h | 打字机效果 |
| 多 LLM 支持 | P1 | 8h | 配置切换界面 |

### 3.2 Phase 2 开发计划

```
Week 1-2: 核心功能
├── 图谱可视化完善
│   ├── 节点筛选面板
│   ├── 关系类型过滤
│   ├── 搜索定位
│   └── PNG/JSON 导出
├── 代码查看器完善
│   ├── 左侧文件树
│   ├── Git 历史版本切换
│   └── 代码 diff 显示
└── 对话历史
    ├── 保存到 PostgreSQL
    ├── 会话列表
    └── 加载历史对话

Week 3: 集成测试
├── 用户体验优化
├── 性能优化
└── Bug 修复
```

---

## 四、Phase 3-A: Automation Sync (自动化同步)

### 4.1 功能列表

| 功能 | 优先级 | 预计工时 | 说明 |
|------|--------|----------|------|
| Webhook 接收 | P1 | 12h | GitLab/GitHub Push Event |
| Webhook 验证 | P1 | 4h | 签名验证、防重放 |
| 定时增量同步 | P2 | 8h | 定时检查远程更新 |
| 手动触发解析 | P1 | 4h | API 端点 |
| 同步进度监控 | P2 | 4h | 进度条、状态显示 |

### 4.2 Phase 3-A 开发计划

```
Week 1: Webhook 实现
├── Webhook 接收端点
├── GitLab 签名验证
├── GitHub HMAC 验证
├── 防重放攻击
└── 增量解析触发

Week 2: 同步机制
├── 定时任务调度
├── 增量解析逻辑
├── 手动触发 API
├── 进度监控
└── 测试验证
```

---

## 五、Phase 3-B: Business Semantic (业务语义扩展)

> 注意: 此阶段为可选功能，根据实际需求决定是否实施

### 5.1 功能列表

| 功能 | 优先级 | 预计工时 | 说明 |
|------|--------|----------|------|
| 业务域推断 | P2 | 16h | LLM 识别业务域 |
| 业务流程识别 | P2 | 16h | 流程/步骤推断 |
| 实体关系提取 | P2 | 12h | 业务实体识别 |
| NL-to-Cypher | P2 | 12h | 自然语言生成查询 |
| 高级查询模板 | P2 | 8h | 业务相关模板 |

### 5.2 技术方案

```java
// 业务语义推断服务
@Service
public class BusinessSemanticService {

    private final ChatLanguageModel llm;

    public void inferBusinessDomain(Project project, List<Method> methods) {
        // 1. 收集方法名、注释、调用链
        // 2. 调用 LLM 推断业务域
        // 3. 创建 Domain 节点
        // 4. 建立方法与域的关系
    }

    public void inferBusinessFlow(Project project, List<Method> methods) {
        // 1. 识别入口方法 (@GetMapping 等)
        // 2. 追踪调用链
        // 3. LLM 识别流程步骤
        // 4. 创建 BusinessFlow/Step 节点
    }
}
```

---

## 六、Phase 4: Production Ready (生产就绪)

### 6.1 功能列表

| 功能 | 优先级 | 预计工时 | 说明 |
|------|--------|----------|------|
| Docker 镜像构建 | P1 | 8h | 多阶段构建 |
| Docker Compose 完善 | P1 | 4h | 生产配置 |
| 性能优化 | P1 | 16h | 大仓库、查询优化 |
| 用户手册 | P1 | 8h | 使用文档 |
| 部署文档 | P1 | 4h | 运维文档 |
| API 文档 | P2 | 4h | Swagger UI |

### 6.2 生产部署架构

```yaml
# docker-compose.prod.yml
services:
  neo4j:
    image: neo4j:5.15
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 8G

  postgres:
    image: postgres:15
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G

  backend:
    build: ./backend
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 4G
    environment:
      - SPRING_PROFILES_ACTIVE=prod

  frontend:
    build: ./frontend
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 512M

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
```

---

## 七、风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| MCP Java SDK 不稳定 | 高 | 中 | 准备自建 Stdio 封装方案 |
| 大型仓库解析慢 | 中 | 高 | 分批解析 + 进度反馈 |
| LLM Token 限制 | 中 | 中 | 上下文优化 + 动态截断 |
| Neo4j 写入性能 | 中 | 低 | 批量插入 + 索引优化 |
| 前端性能问题 | 中 | 中 | 虚拟滚动 + 懒加载 |

---

## 八、下一步行动

### 立即开始 (本周)

1. **P0-01**: 实现 `parseNodes()` 方法
2. **P0-02**: 实现 `parseEdges()` 方法
3. **P0-03**: GraphRAG 端到端测试
4. **P0-04**: 前后端联调

### 下周计划

1. **P1-01**: D3.js 图谱可视化
2. **P1-02**: Monaco Editor 集成
3. **P1-03**: SSE 流式输出

### 里程碑检查点

- **Week 1 结束**: 解析功能完成，可正常导入项目
- **Week 2 结束**: 前端可视化完成，基础可演示
- **Week 3 结束**: Phase 1 完成，进入 Phase 2

---

## 九、资源需求

### 开发环境

| 资源 | 配置 | 用途 |
|------|------|------|
| CPU | 8 核+ | 后端 + 容器 |
| 内存 | 16 GB+ | Neo4j + IDE |
| 存储 | 100 GB SSD | 代码仓库 + 数据库 |

### 生产环境 (推荐)

| 组件 | CPU | 内存 | 存储 |
|------|-----|------|------|
| Neo4j | 4 核 | 8 GB | 200 GB SSD |
| PostgreSQL | 2 核 | 2 GB | 50 GB SSD |
| Redis | 1 核 | 1 GB | - |
| Backend | 4 核 | 4 GB | - |
| Frontend | 1 核 | 512 MB | - |
| **总计** | **12 核** | **16 GB** | **250 GB SSD** |

---

**文档版本**: v1.0
**创建日期**: 2026-06-24
**下次更新**: Phase 1 完成后
