# 部署指南

本文档介绍如何部署代码知识图谱问答系统。

## 目录

- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [生产部署](#生产部署)
- [监控与维护](#监控与维护)
- [常见问题](#常见问题)

---

## 环境要求

### 最低配置

| 组件 | CPU | 内存 | 存储 |
|------|-----|------|------|
| Neo4j | 2 核 | 4 GB | 50 GB SSD |
| PostgreSQL | 1 核 | 1 GB | 10 GB SSD |
| Redis | 1 核 | 512 MB | - |
| Backend | 2 核 | 2 GB | - |
| Frontend | 1 核 | 256 MB | - |
| **总计** | **7 核** | **8 GB** | **60+ GB SSD** |

### 推荐配置

| 组件 | CPU | 内存 | 存储 |
|------|-----|------|------|
| Neo4j | 4 核 | 8 GB | 200 GB SSD |
| PostgreSQL | 2 核 | 2 GB | 50 GB SSD |
| Redis | 1 核 | 1 GB | - |
| Backend | 4 核 | 4 GB | - |
| Frontend | 1 核 | 512 MB | - |
| **总计** | **12 核** | **16 GB** | **250 GB SSD** |

### 软件要求

- Docker 20.10+
- Docker Compose 2.0+
- Git

---

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/DaiOwen/code-knowledge-graph.git
cd code-knowledge-graph
```

### 2. 创建环境变量文件

```bash
cp .env.example .env
```

编辑 `.env` 文件，设置必要的配置：

```env
# PostgreSQL
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-secure-password

# Neo4j
NEO4J_PASSWORD=your-secure-password

# LLM API
LLM_PROVIDER=openai
LLM_API_KEY=sk-your-api-key
LLM_MODEL=gpt-4o

# JWT
JWT_SECRET=your-jwt-secret-at-least-256-bits
```

### 3. 启动服务

```bash
# 启动基础设施
docker-compose up -d neo4j postgres redis

# 等待服务就绪
sleep 30

# 启动应用
docker-compose up -d
```

### 4. 访问应用

- 前端: http://localhost
- 后端 API: http://localhost:8080/api
- Neo4j Browser: http://localhost:7474

---

## 配置说明

### 后端配置

后端配置通过环境变量设置：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 运行环境 | `prod` |
| `LLM_PROVIDER` | LLM 提供者 | `openai` |
| `LLM_API_KEY` | API 密钥 | - |
| `LLM_MODEL` | 模型名称 | `gpt-4o` |
| `LLM_BASE_URL` | API 端点 | `https://api.openai.com/v1` |
| `JWT_SECRET` | JWT 密钥 | - |
| `JWT_EXPIRATION` | Token 过期时间(ms) | `86400000` |

### LLM 提供者配置

#### OpenAI

```env
LLM_PROVIDER=openai
LLM_API_KEY=sk-xxx
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
LLM_API_KEY=sk-xxx
LLM_MODEL=qwen-max
```

#### DeepSeek

```env
LLM_PROVIDER=deepseek
LLM_API_KEY=sk-xxx
LLM_MODEL=deepseek-chat
```

### Neo4j 配置

生产环境建议调整内存配置：

```yaml
environment:
  - NEO4J_dbms_memory_heap_initial__size=2G
  - NEO4J_dbms_memory_heap_max__size=4G
  - NEO4J_dbms_memory_pagecache_size=2G
```

---

## 生产部署

### 使用 Docker Compose

```bash
# 使用生产配置启动
docker-compose -f docker-compose.prod.yml up -d
```

### 使用 Kubernetes

#### 1. 创建命名空间

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ckg
```

#### 2. 创建 Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ckg-secrets
  namespace: ckg
type: Opaque
stringData:
  postgres-password: your-password
  neo4j-password: your-password
  llm-api-key: your-api-key
  jwt-secret: your-jwt-secret
```

#### 3. 部署服务

参考 `k8s/` 目录下的部署清单。

### 数据备份

#### Neo4j 备份

```bash
# 在线备份
docker exec ckg-neo4j neo4j-admin database backup neo4j --to-path=/backups

# 离线备份
docker exec ckg-neo4j neo4j-admin dump neo4j --to=/backups/neo4j.dump
```

#### PostgreSQL 备份

```bash
docker exec ckg-postgres pg_dump -U postgres ckg > backup.sql
```

---

## 监控与维护

### 健康检查

```bash
# 后端健康检查
curl http://localhost:8080/api/health

# Neo4j 健康检查
curl http://localhost:7474

# PostgreSQL 健康检查
docker exec ckg-postgres pg_isready
```

### 日志查看

```bash
# 查看所有日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
```

### 性能监控

推荐使用：
- Prometheus + Grafana 监控
- Neo4j Dashboard (http://localhost:7474)
- Spring Boot Actuator (http://localhost:8080/actuator)

---

## 常见问题

### Q: Neo4j 启动失败

检查内存配置是否合理，确保有足够的堆内存：

```bash
docker logs ckg-neo4j
```

### Q: 后端无法连接 Neo4j

检查网络配置和认证信息：

```bash
# 测试连接
docker exec ckg-backend nc -zv neo4j 7687
```

### Q: LLM API 调用失败

1. 检查 API Key 是否正确
2. 检查网络是否可访问 API 端点
3. 检查 API 配额是否用尽

### Q: 前端无法访问后端 API

检查 Nginx 配置和后端服务状态：

```bash
# 检查 Nginx 配置
docker exec ckg-frontend nginx -t

# 重启 Nginx
docker exec ckg-frontend nginx -s reload
```

### Q: 内存不足

调整 JVM 参数和容器内存限制：

```yaml
environment:
  - JAVA_OPTS=-XX:MaxRAMPercentage=75.0
deploy:
  resources:
    limits:
      memory: 4G
```

---

## 安全建议

1. **修改默认密码**: 所有服务的默认密码必须修改
2. **启用 HTTPS**: 生产环境必须使用 HTTPS
3. **网络隔离**: 将服务部署在私有网络中
4. **定期备份**: 设置自动备份策略
5. **更新依赖**: 定期更新 Docker 镜像和依赖

---

## 联系支持

如有问题，请在 GitHub 上提交 Issue: https://github.com/DaiOwen/code-knowledge-graph/issues
