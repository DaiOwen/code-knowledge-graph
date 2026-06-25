# Code Knowledge Graph - 代码知识图谱问答系统

基于 GraphRAG 的智能代码分析平台，支持代码结构解析、业务语义推断、自然语言问答和代码溯源。

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🔍 **代码结构解析** | 类、方法、字段、调用关系、继承关系 |
| 🧠 **智能问答** | 自然语言查询代码，"谁调用了 createOrder？" |
| 📊 **图谱可视化** | D3.js 力导向图展示调用关系 |
| 🔗 **代码溯源** | Git 提交历史追溯，定位代码作者 |
| 📝 **代码定位** | Monaco Editor 点击跳转到代码位置 |
| 🔄 **自动同步** | Webhook + 定时任务自动更新图谱 |
| 🌐 **多 LLM 支持** | OpenAI / 通义千问 / DeepSeek |

## 🛠 技术架构

- **后端**: Spring Boot 3.x + Neo4j + PostgreSQL + Redis + LangChain4j
- **前端**: Vue3 + TypeScript + Vite + Element Plus + D3.js + Monaco Editor
- **代码解析**: codegraph MCP
- **LLM**: OpenAI / Azure / 通义千问 / DeepSeek / Ollama

## 🚀 快速开始

### 方式一：Docker Compose（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/DaiOwen/code-knowledge-graph.git
cd code-knowledge-graph

# 2. 创建配置文件
cp .env.example .env
# 编辑 .env 文件，配置 LLM_API_KEY 等必要参数

# 3. 启动所有服务
docker-compose -f docker-compose.prod.yml up -d

# 4. 访问系统
# 前端: http://localhost
# 后端: http://localhost:8080/api
# Neo4j: http://localhost:7474
```

### 方式二：本地开发

```bash
# 1. 启动基础设施
docker-compose up -d neo4j postgres redis

# 2. 启动后端
cd backend
export LLM_API_KEY=your-api-key
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

### 环境要求

| 软件 | 版本 |
|------|------|
| Docker | 20.10+ |
| Docker Compose | 2.0+ |
| Java | 17+ (本地开发) |
| Node.js | 18+ (本地开发) |

## 📖 详细文档

| 文档 | 说明 |
|------|------|
| [**使用说明**](docs/USAGE.md) | 完整使用指南：快速启动、项目管理、代码同步、智能问答 |
| [部署指南](docs/deployment/DEPLOYMENT.md) | 生产环境部署配置 |
| [用户手册](docs/user/USER_GUIDE.md) | 功能使用说明 |
| [架构设计](docs/architecture/ARCHITECTURE.md) | 系统架构文档 |
| [进度报告](docs/PROGRESS.md) | 开发进度追踪 |

## 🎯 使用示例

### 智能问答

```
问题: "谁调用了 createOrder 方法？"

回答:
createOrder 方法被以下位置调用：

1. OrderController.createOrder()
   文件: src/main/java/OrderController.java:25

2. OrderService.processOrder()
   文件: src/main/java/OrderService.java:42
```

### 图谱可视化

- 蓝色节点：类/接口
- 绿色节点：方法
- 绿色箭头：调用关系

### 代码同步

支持三种同步方式：
1. **Webhook 自动触发** - GitLab/GitHub/Gitee Push Event
2. **定时增量同步** - 每小时检查更新
3. **手动触发** - API 或界面操作

## 📁 项目结构

```
code-knowledge-graph/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/       # Java 源码
│   ├── Dockerfile           # 后端镜像
│   └── pom.xml              # Maven 配置
├── frontend/                # Vue3 前端
│   ├── src/                 # 前端源码
│   ├── Dockerfile           # 前端镜像
│   └── package.json         # NPM 配置
├── docker/                  # Docker 配置
│   └── nginx/               # Nginx 配置
├── docs/                    # 文档
│   ├── USAGE.md             # 详细使用说明 ⭐
│   ├── PROGRESS.md          # 开发进度
│   ├── deployment/          # 部署文档
│   └── user/                # 用户手册
├── docker-compose.yml       # 开发环境编排
├── docker-compose.prod.yml  # 生产环境编排
└── .env.example             # 环境变量模板
```

## 🔧 配置说明

### 必要配置

```env
# .env 文件

# LLM API (必填)
LLM_PROVIDER=openai           # 或 qwen, deepseek
LLM_API_KEY=sk-your-key       # API 密钥
LLM_MODEL=gpt-4o              # 模型名称

# 数据库密码 (建议修改)
POSTGRES_PASSWORD=your-password
NEO4J_PASSWORD=your-password

# JWT 密钥 (建议修改)
JWT_SECRET=your-jwt-secret
```

### LLM 提供者配置

| 提供者 | 配置示例 |
|--------|----------|
| OpenAI | `LLM_PROVIDER=openai`, `LLM_MODEL=gpt-4o` |
| 通义千问 | `LLM_PROVIDER=qwen`, `LLM_MODEL=qwen-max` |
| DeepSeek | `LLM_PROVIDER=deepseek`, `LLM_MODEL=deepseek-chat` |

## 📊 开发进度

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段零 | 技术验证 | ✅ 完成 |
| 阶段一 | MVP 核心功能 | ✅ 完成 |
| 阶段二 | 体验增强 | ✅ 完成 |
| 阶段三 | 自动化与扩展 | ✅ 完成 |
| 阶段四 | 生产就绪 | ✅ 完成 |

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 License

MIT License
