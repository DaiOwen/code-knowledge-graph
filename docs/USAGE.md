# 代码知识图谱问答系统 - 详细使用说明

> 版本: v1.0.0  
> 更新日期: 2026-06-25

---

## 目录

- [系统概述](#系统概述)
- [环境准备](#环境准备)
- [快速启动](#快速启动)
- [项目管理](#项目管理)
- [代码解析](#代码解析)
- [代码同步](#代码同步)
- [智能问答](#智能问答)
- [图谱可视化](#图谱可视化)
- [配置说明](#配置说明)
- [API 接口](#api-接口)
- [常见问题](#常见问题)

---

## 系统概述

代码知识图谱问答系统是一个基于 GraphRAG 的智能代码分析平台，支持：

| 功能 | 说明 |
|------|------|
| 代码解析 | 自动解析 Java/TypeScript/Python 等代码结构 |
| 智能问答 | 通过自然语言查询代码信息 |
| 图谱可视化 | D3.js 展示代码调用关系 |
| 代码溯源 | 追踪代码修改历史和作者 |
| 自动同步 | Webhook/定时任务自动更新图谱 |

---

## 环境准备

### 系统要求

| 组件 | 最低配置 | 推荐配置 |
|------|----------|----------|
| CPU | 4 核 | 8 核+ |
| 内存 | 8 GB | 16 GB+ |
| 存储 | 50 GB SSD | 200 GB SSD |
| 操作系统 | Linux/macOS/Windows | Linux |

### 软件依赖

| 软件 | 版本要求 | 用途 |
|------|----------|------|
| Docker | 20.10+ | 容器运行 |
| Docker Compose | 2.0+ | 服务编排 |
| Git | 2.0+ | 代码克隆 |
| Node.js | 18+ | 前端开发（可选） |
| Java | 17+ | 后端开发（可选） |

### LLM API 准备

系统需要 LLM API 才能运行智能问答功能。支持以下提供商：

| 提供商 | 获取方式 | 推荐 |
|--------|----------|------|
| OpenAI | https://platform.openai.com | gpt-4o |
| 通义千问 | https://dashscope.console.aliyun.com | qwen-max |
| DeepSeek | https://platform.deepseek.com | deepseek-chat |

---

## 快速启动

### 方式一：Docker Compose（推荐）

#### 1. 克隆项目

```bash
git clone https://github.com/DaiOwen/code-knowledge-graph.git
cd code-knowledge-graph
```

#### 2. 创建配置文件

```bash
cp .env.example .env
```

#### 3. 编辑配置文件

```bash
# 编辑 .env 文件，填入必要的配置
vim .env
```

必须配置项：

```env
# PostgreSQL 数据库密码
POSTGRES_PASSWORD=your-secure-password

# Neo4j 数据库密码
NEO4J_PASSWORD=your-secure-password

# LLM API 配置（选择一个）
# OpenAI:
LLM_PROVIDER=openai
LLM_API_KEY=sk-your-openai-key
LLM_MODEL=gpt-4o

# 或通义千问:
# LLM_PROVIDER=qwen
# LLM_API_KEY=sk-your-dashscope-key
# LLM_MODEL=qwen-max

# JWT 密钥（随机生成一个长字符串）
JWT_SECRET=your-random-jwt-secret-at-least-256-bits-long
```

#### 4. 启动服务

```bash
# 启动所有服务
docker-compose -f docker-compose.prod.yml up -d

# 查看启动状态
docker-compose -f docker-compose.prod.yml ps
```

#### 5. 检查服务状态

```bash
# 等待服务启动完成（约 30-60 秒）
# 检查后端健康状态
curl http://localhost:8080/api/health

# 检查 Neo4j 状态
curl http://localhost:7474
```

#### 6. 访问系统

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端界面 | http://localhost | Web 界面 |
| 后端 API | http://localhost:8080/api | REST API |
| Neo4j Browser | http://localhost:7474 | 图数据库管理 |

---

### 方式二：本地开发模式

#### 1. 启动基础设施

```bash
# 仅启动数据库服务
docker-compose up -d neo4j postgres redis
```

#### 2. 启动后端

```bash
cd backend

# 配置环境变量
export LLM_API_KEY=your-api-key
export LLM_MODEL=gpt-4o

# 启动
mvn spring-boot:run
```

#### 3. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

#### 4. 访问

- 前端: http://localhost:5173
- 后端: http://localhost:8080/api

---

## 项目管理

### 创建项目

#### 通过界面创建

1. 登录系统后，点击左侧导航「项目管理」
2. 点击右上角「新建项目」按钮
3. 填写项目信息：

| 字段 | 说明 | 示例 |
|------|------|------|
| 项目名称 | 项目显示名称 | 订单系统 |
| Git 地址 | Git 仓库 URL | https://github.com/xxx/order-service.git |
| 分支 | 要解析的分支 | main |
| 语言 | 主要编程语言 | Java |
| 描述 | 项目描述（可选） | 订单微服务 |

4. 点击「保存」完成创建

#### 通过 API 创建

```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "订单系统",
    "gitUrl": "https://github.com/xxx/order-service.git",
    "branch": "main",
    "language": "Java"
  }'
```

### 项目状态说明

| 状态 | 说明 |
|------|------|
| PENDING | 新创建，待解析 |
| PARSING | 正在解析中 |
| READY | 解析完成，可以使用 |
| ERROR | 解析失败 |

---

## 代码解析

### 触发解析

#### 方式一：通过界面

1. 进入「项目管理」页面
2. 找到目标项目，点击「解析」按钮
3. 选择解析类型：
   - **全量解析**：解析所有代码
   - **增量解析**：仅解析变更部分
4. 确认后等待解析完成

#### 方式二：通过 API

```bash
# 触发全量解析
curl -X POST http://localhost:8080/api/parse/full/{projectId} \
  -H "Authorization: Bearer YOUR_TOKEN"

# 触发增量解析（需要先完成全量解析）
curl -X POST http://localhost:8080/api/parse/incremental/{projectId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 查看解析进度

```bash
# 查询解析进度
curl http://localhost:8080/api/parse/progress/{projectId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

返回示例：

```json
{
  "success": true,
  "data": {
    "status": "RUNNING",
    "progress": 45,
    "type": "FULL",
    "startedAt": "2026-06-25T10:30:00",
    "message": ""
  }
}
```

### 解析流程说明

```
解析流程:
├── Step 1: 克隆仓库 (20%)
│   └── 克隆 Git 仓库到本地
├── Step 2: 启动 MCP (30%)
│   └── 启动 codegraph 解析引擎
├── Step 3: 解析代码结构 (50%)
│   ├── 解析类/接口
│   ├── 解析方法/字段
│   └── 提取调用关系
├── Step 4: 写入 Neo4j (70%)
│   ├── 创建节点
│   └── 创建关系
├── Step 5: 解析 Git 历史 (90%)
│   ├── 提取提交记录
│   └── 关联作者信息
└── Step 6: 完成 (100%)
```

---

## 代码同步

### 自动同步方式

系统支持三种自动同步方式：

#### 1. Webhook 自动触发

**GitLab 配置：**

1. 进入 GitLab 项目 → Settings → Webhooks
2. 填写配置：

| 字段 | 值 |
|------|------|
| URL | http://your-server/api/webhook/gitlab/{projectId} |
| Secret Token | 项目配置的 webhookSecret |
| Trigger | 勾选 Push events |
| Branch filter | 可选，指定分支 |

3. 点击「Add webhook」保存
4. 点击「Test」测试连接

**GitHub 配置：**

1. 进入 GitHub 项目 → Settings → Webhooks
2. 点击「Add webhook」
3. 填写配置：

| 字段 | 值 |
|------|------|
| Payload URL | http://your-server/api/webhook/github/{projectId} |
| Content type | application/json |
| Secret | 项目配置的 webhookSecret |
| Events | 选择「Just the push event」 |

4. 点击「Add webhook」保存

**Gitee 配置：**

1. 进入 Gitee 项目 → 管理 → WebHooks
2. 填写配置：

| 字段 | 值 |
|------|------|
| URL | http://your-server/api/webhook/gitee/{projectId} |
| 密码 | 项目配置的 webhookSecret |
| 勾选 | Push |

3. 点击「添加」保存

#### 2. 定时增量同步

系统默认每小时检查一次远程仓库更新：

```bash
# 定时任务配置（application.yml）
spring:
  task:
    scheduling:
      cron: "0 0 * * * ?"  # 每小时执行
```

#### 3. 手动触发同步

**通过界面：**

1. 进入项目详情页
2. 点击「同步代码」按钮

**通过 API：**

```bash
curl -X POST http://localhost:8080/api/parse/sync/{projectId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 同步策略说明

| 场景 | 触发方式 | 处理逻辑 |
|------|----------|----------|
| 首次解析 | 手动触发 | 全量解析所有代码 |
| 代码推送 | Webhook | 解析变更文件 |
| 定时检查 | 定时任务 | 检测到更新后增量解析 |
| 手动同步 | API/界面 | 拉取最新代码后增量解析 |

---

## 智能问答

### 问法示例

系统支持以下类型的自然语言问题：

#### 调用链查询

```
问题示例：
- "谁调用了 createOrder 方法？"
- "哪些地方调用了 validateOrder？"
- "OrderService.createOrder 被谁调用？"

返回信息：
- 调用者方法名
- 文件路径和行号
- 调用关系图
```

#### 影响分析

```
问题示例：
- "修改 createOrder 会影响什么？"
- "改动 validateOrder 的影响范围？"
- "如果删除 checkStock 方法会怎样？"

返回信息：
- 受影响的方法列表
- 影响深度（直接/间接）
- 依赖关系图
```

#### 代码溯源

```
问题示例：
- "createOrder 方法是谁写的？"
- "谁最后修改了 OrderService？"
- "张三提交了哪些代码？"

返回信息：
- 作者姓名和邮箱
- 提交时间和哈希
- 提交说明
```

#### 类方法查询

```
问题示例：
- "OrderService 类有哪些方法？"
- "InventoryService 的所有方法"
- "Order 类包含什么字段？"

返回信息：
- 方法/字段列表
- 方法签名
- 代码位置
```

#### 修改历史

```
问题示例：
- "createOrder 方法的修改历史"
- "OrderService 最近谁改过？"
- "上周都有谁提交了代码？"

返回信息：
- 提交记录列表
- 每次提交的详情
- 时间线展示
```

### 使用技巧

1. **使用引号提高准确度**
   ```
   推荐: 谁调用了 `createOrder` 方法？
   ```

2. **指定完整类名避免歧义**
   ```
   推荐: OrderService.createOrder 被谁调用？
   而非: createOrder 被谁调用？（可能有重名方法）
   ```

3. **组合查询条件**
   ```
   示例: "订单模块中张三上周修改的代码"
   ```

4. **查看来源定位**
   ```
   点击回答中的文件链接可跳转到具体代码行
   ```

---

## 图谱可视化

### 界面操作

#### 基本操作

| 操作 | 说明 |
|------|------|
| 鼠标拖拽 | 移动画布 |
| 滚轮 | 缩放视图 |
| 点击节点 | 查看节点详情 |
| 拖拽节点 | 调整节点位置 |

#### 工具栏功能

| 按钮 | 功能 |
|------|------|
| 🔍 搜索框 | 搜索节点名称 |
| 节点类型筛选 | 筛选类/方法/字段 |
| 放大/缩小 | 调整视图比例 |
| 重置 | 恢复默认视图 |
| 导出 | 导出为 SVG 图片 |

### 节点颜色说明

| 颜色 | 类型 | 说明 |
|------|------|------|
| 🔵 蓝色 | 类/接口 | 代码中的类定义 |
| 🟢 绿色 | 方法 | 类中的方法 |
| 🟡 黄色 | 字段 | 类中的属性字段 |

### 关系线说明

| 线条类型 | 关系 | 说明 |
|----------|------|------|
| 绿色实线箭头 | CALLS | 方法调用关系 |
| 蓝色虚线 | EXTENDS | 类继承关系 |
| 蓝色虚线 | IMPLEMENTS | 接口实现关系 |

### 节点详情面板

点击节点后右侧弹出详情面板，包含：

```
节点详情:
├── 基本信息
│   ├── 名称
│   ├── 类型
│   ├── 文件路径（可点击跳转）
│   └── 行号
├── 调用关系
│   ├── 调用者标签页（谁调用了它）
│   └── 被调用标签页（它调用了谁）
└── 操作按钮
    ├── 影响分析
    └── 提问
```

---

## 配置说明

### 环境变量配置

| 变量 | 必填 | 说明 | 默认值 |
|------|------|------|--------|
| `POSTGRES_USER` | 否 | PostgreSQL 用户名 | postgres |
| `POSTGRES_PASSWORD` | 是 | PostgreSQL 密码 | - |
| `NEO4J_PASSWORD` | 是 | Neo4j 密码 | - |
| `LLM_PROVIDER` | 是 | LLM 提供者 | openai |
| `LLM_API_KEY` | 是 | LLM API 密钥 | - |
| `LLM_MODEL` | 否 | 模型名称 | gpt-4o |
| `LLM_BASE_URL` | 否 | API 端点 | https://api.openai.com/v1 |
| `JWT_SECRET` | 是 | JWT 密钥 | - |

### LLM 配置示例

#### OpenAI

```env
LLM_PROVIDER=openai
LLM_API_KEY=sk-proj-xxxxx
LLM_MODEL=gpt-4o
```

#### Azure OpenAI

```env
LLM_PROVIDER=azure
LLM_API_KEY=your-azure-key
LLM_MODEL=gpt-4
LLM_BASE_URL=https://your-resource.openai.azure.com
```

#### 通义千问

```env
LLM_PROVIDER=qwen
LLM_API_KEY=sk-xxxxx
LLM_MODEL=qwen-max
```

#### DeepSeek

```env
LLM_PROVIDER=deepseek
LLM_API_KEY=sk-xxxxx
LLM_MODEL=deepseek-chat
```

### 性能调优

#### Neo4j 内存配置

```yaml
# docker-compose.prod.yml
environment:
  - NEO4J_dbms_memory_heap_initial__size=2G
  - NEO4J_dbms_memory_heap_max__size=4G
  - NEO4J_dbms_memory_pagecache_size=2G
```

#### 后端 JVM 配置

```yaml
# docker-compose.prod.yml
environment:
  - JAVA_OPTS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC
```

---

## API 接口

### 认证接口

#### 登录

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "username": "admin"
  }
}
```

#### 注册

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "newuser",
  "password": "password",
  "email": "user@example.com"
}
```

### 项目接口

#### 获取项目列表

```http
GET /api/projects
Authorization: Bearer {token}
```

#### 创建项目

```http
POST /api/projects
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "项目名称",
  "gitUrl": "https://github.com/xxx/repo.git",
  "branch": "main",
  "language": "Java"
}
```

#### 获取项目详情

```http
GET /api/projects/{id}
Authorization: Bearer {token}
```

#### 删除项目

```http
DELETE /api/projects/{id}
Authorization: Bearer {token}
```

### 解析接口

#### 触发全量解析

```http
POST /api/parse/full/{projectId}
Authorization: Bearer {token}
```

#### 触发增量解析

```http
POST /api/parse/incremental/{projectId}
Authorization: Bearer {token}
```

#### 查询解析进度

```http
GET /api/parse/progress/{projectId}
Authorization: Bearer {token}
```

### 问答接口

#### 提问

```http
POST /api/qa/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "projectId": 1,
  "question": "谁调用了 createOrder 方法？",
  "sessionId": null
}
```

响应：

```json
{
  "success": true,
  "data": {
    "answer": "createOrder 方法被以下位置调用...",
    "citations": [
      {
        "filePath": "src/main/java/OrderController.java",
        "line": 25
      }
    ]
  }
}
```

#### 流式问答 (SSE)

```http
POST /api/qa/stream
Authorization: Bearer {token}
Content-Type: application/json
Accept: text/event-stream

{
  "projectId": 1,
  "question": "谁调用了 createOrder？"
}
```

### 图谱接口

#### 获取项目图谱

```http
GET /api/graph/{projectId}?nodeType=method&limit=100
Authorization: Bearer {token}
```

#### 获取方法调用者

```http
GET /api/graph/{projectId}/callers?method=createOrder
Authorization: Bearer {token}
```

#### 获取方法被调用

```http
GET /api/graph/{projectId}/callees?method=createOrder
Authorization: Bearer {token}
```

#### 影响分析

```http
GET /api/graph/{projectId}/impact?method=createOrder&depth=3
Authorization: Bearer {token}
```

### Webhook 接口

#### GitLab Webhook

```http
POST /api/webhook/gitlab/{projectId}
X-Gitlab-Token: {webhookSecret}
X-Gitlab-Event: Push Hook
Content-Type: application/json

{
  "ref": "refs/heads/main",
  "before": "abc123",
  "after": "def456",
  "commits": [...]
}
```

#### GitHub Webhook

```http
POST /api/webhook/github/{projectId}
X-Hub-Signature-256: sha256={hmacSignature}
X-GitHub-Event: push
Content-Type: application/json

{
  "ref": "refs/heads/main",
  "before": "abc123",
  "after": "def456",
  "commits": [...]
}
```

---

## 常见问题

### Q1: Docker 启动失败？

**错误信息**: `port is already allocated`

**解决方案**: 检查端口占用

```bash
# Linux/macOS
lsof -i :8080
lsof -i :7474

# Windows
netstat -ano | findstr :8080
```

### Q2: Neo4j 连接失败？

**错误信息**: `Unable to connect to localhost:7687`

**解决方案**:

1. 检查 Neo4j 容器状态

```bash
docker logs ckg-neo4j
```

2. 确认 Neo4j 已启动完成（需要等待 30-60 秒）

```bash
curl http://localhost:7474
```

### Q3: 解析一直失败？

**可能原因**:

1. **Git 仓库无法访问**
   ```bash
   # 测试仓库是否可访问
   git clone https://github.com/xxx/repo.git
   ```

2. **内存不足**
   ```yaml
   # 增加容器内存限制
   deploy:
     resources:
       limits:
         memory: 4G
   ```

3. **codegraph 未安装**
   ```bash
   # 确保 codegraph 可用
   codegraph --version
   ```

### Q4: 问答返回空结果？

**解决方案**:

1. 确认项目状态为 `READY`
2. 确认图谱中有数据
   ```bash
   # 查询 Neo4j 节点数量
   curl -u neo4j:password http://localhost:7474/db/neo4j/tx/commit \
     -H "Content-Type: application/json" \
     -d '{"statements":[{"statement":"MATCH (n) RETURN count(n)"}]}'
   ```

### Q5: LLM API 调用失败？

**检查步骤**:

1. 确认 API Key 正确
2. 确认网络可访问 API 端点
3. 确认 API 配额充足

```bash
# 测试 OpenAI API
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer YOUR_API_KEY"
```

### Q6: Webhook 不触发？

**排查步骤**:

1. 确认 Webhook URL 可从外网访问
2. 检查 Webhook Secret 配置正确
3. 查看后端日志

```bash
docker logs ckg-backend | grep webhook
```

### Q7: 前端页面空白？

**解决方案**:

1. 检查浏览器控制台错误
2. 确认后端 API 正常
3. 清除浏览器缓存

### Q8: 如何重置密码？

**数据库方式**:

```sql
-- 连接 PostgreSQL
docker exec -it ckg-postgres psql -U postgres -d ckg

-- 重置密码（需要 BCrypt 加密）
UPDATE users SET password = '$2a$10$...' WHERE username = 'admin';
```

---

## 技术支持

- **GitHub Issues**: https://github.com/DaiOwen/code-knowledge-graph/issues
- **文档**: 项目 `docs/` 目录

---

**最后更新**: 2026-06-25
