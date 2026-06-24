# Code Knowledge Graph - 代码知识图谱问答系统

基于 GraphRAG 的智能代码分析平台，支持代码结构解析、业务语义推断、自然语言问答和代码溯源。

## 功能特性

- 🔍 **代码结构解析**: 类、方法、字段、调用关系、继承关系
- 🧠 **业务语义推断**: 业务域、业务流程、业务实体自动识别
- 💬 **自然语言问答**: "订单下单流程有哪些步骤？" "createOrder 方法谁写的？"
- 📊 **图谱可视化**: D3.js/ECharts 调用链可视化
- 🔗 **代码溯源**: Git 提交历史追溯
- 📝 **代码定位**: Monaco Editor 点击跳转到代码位置

## 技术架构

- **后端**: Spring Boot 3.x + Neo4j + PostgreSQL + Redis + LangChain4j
- **前端**: Vue3 + TypeScript + Vite + Element Plus + D3.js + Monaco Editor
- **代码解析**: codegraph MCP
- **LLM**: OpenAI / Azure / 通义千问 / DeepSeek / Ollama

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Docker & Docker Compose
- Neo4j 5.x
- PostgreSQL 15+
- Redis 7+

### 启动服务

```bash
# 克隆项目
git clone https://github.com/DaiOwen/code-knowledge-graph.git
cd code-knowledge-graph

# 启动基础设施服务
docker-compose up -d neo4j postgres redis

# 启动后端
cd backend
mvn spring-boot:run

# 启动前端
cd frontend
npm install
npm run dev
```

### 访问应用

- 前端: http://localhost:5173
- 后端 API: http://localhost:8080
- Neo4j Browser: http://localhost:7474

## 项目结构

```
code-knowledge-graph/
├── backend/                 # Spring Boot 后端
├── frontend/                # Vue3 前端
├── docker/                  # Docker 配置
├── docs/                    # 文档
│   ├── api/                 # API 文档
│   ├── deployment/          # 部署文档
│   ├── user/                # 用户手册
│   └── architecture/        # 架构设计
└── scripts/                 # 工具脚本
```

## 文档

- [设计文档](docs/architecture/ARCHITECTURE.md)
- [API 文档](docs/api/API.md)
- [部署指南](docs/deployment/DEPLOYMENT.md)
- [用户手册](docs/user/USER_GUIDE.md)

## 开发进度

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段零 | 技术验证 | 进行中 |
| 阶段一 | MVP 核心功能 | 待开始 |
| 阶段二 | 体验增强 | 待开始 |
| 阶段三 | 自动化与扩展 | 待开始 |
| 阶段四 | 生产就绪 | 待开始 |

## License

MIT License