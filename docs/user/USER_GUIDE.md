# 用户手册

代码知识图谱问答系统用户指南

## 目录

- [系统概述](#系统概述)
- [快速入门](#快速入门)
- [功能详解](#功能详解)
- [常见问答示例](#常见问答示例)

---

## 系统概述

代码知识图谱问答系统是一个基于 GraphRAG 的智能代码分析平台，帮助您：

- 通过自然语言查询代码结构
- 追溯代码修改历史
- 分析代码依赖关系
- 可视化调用链

---

## 快速入门

### 1. 登录系统

访问系统首页，使用用户名和密码登录。

### 2. 创建项目

1. 点击左侧导航栏的「项目管理」
2. 点击「新建项目」按钮
3. 填写项目信息：
   - **项目名称**: 项目显示名称
   - **Git 地址**: GitLab/GitHub 仓库 URL
   - **分支**: 要解析的分支（默认 main）
   - **语言**: 代码主要语言（Java/Python/等）

4. 点击「保存」创建项目

### 3. 解析项目

1. 在项目列表中找到新创建的项目
2. 点击「开始解析」按钮
3. 等待解析完成（进度会实时显示）

解析过程中系统会：
- 克隆 Git 仓库
- 解析代码结构（类、方法、字段）
- 分析调用关系
- 提取提交历史

### 4. 开始提问

解析完成后，点击「智能问答」开始使用。

---

## 功能详解

### 智能问答

输入自然语言问题，系统会基于知识图谱回答。

**支持的问法示例**:
- "谁调用了 `createOrder` 方法？"
- "修改 `validateOrder` 会影响什么？"
- "OrderService 类有哪些方法？"
- "`createOrder` 方法是谁写的？"
- "张三最近提交了哪些代码？"

### 图谱可视化

点击「图谱可视化」查看代码结构图：

- **节点类型**: 类（蓝色）、方法（绿色）、字段（黄色）
- **关系类型**: 调用关系（绿色箭头）、继承关系（蓝色虚线）
- **交互操作**:
  - 鼠标拖拽移动节点
  - 点击节点查看详情
  - 搜索框搜索节点
  - 滚轮缩放视图

### 代码查看器

点击「代码文件」查看源代码：

- 左侧文件树导航
- 代码高亮显示
- 点击问答来源可跳转到具体代码行

### 项目管理

- **项目列表**: 查看所有项目及解析状态
- **解析触发**: 手动触发全量或增量解析
- **Webhook 配置**: 配置自动解析触发

---

## 常见问答示例

### 调用链查询

**问题**: "谁调用了 createOrder 方法？"

**回答示例**:
```
createOrder 方法被以下位置调用：

1. OrderController.createOrder() 
   文件: src/main/java/OrderController.java:25

2. OrderService.processOrder()
   文件: src/main/java/OrderService.java:42
```

### 影响分析

**问题**: "修改 validateOrder 会影响什么？"

**回答示例**:
```
修改 validateOrder 方法可能影响以下 3 个方法：

1. createOrder (OrderService.java:8)
   直接依赖此方法

2. processOrder (OrderService.java:42)
   通过 createOrder 间接依赖

3. handleOrderRequest (OrderController.java:15)
   通过 processOrder 间接依赖
```

### 代码溯源

**问题**: "createOrder 方法是谁写的？"

**回答示例**:
```
createOrder 方法最后修改信息：

- 作者: 张三 (zhangsan@example.com)
- 提交: abc123ef "feat: add order creation logic"
- 时间: 2026-06-15 10:30:00
```

### 类方法查询

**问题**: "OrderService 类有哪些方法？"

**回答示例**:
```
OrderService 类包含以下方法：

1. createOrder(OrderRequest): Order
   文件: src/main/java/OrderService.java:8

2. validateOrder(OrderRequest): void
   文件: src/main/java/OrderService.java:15

3. checkInventory(OrderRequest): void
   文件: src/main/java/OrderService.java:21

4. buildOrder(OrderRequest): Order
   文件: src/main/java/OrderService.java:27

5. calculateTotal(OrderRequest): double
   文件: src/main/java/OrderService.java:35
```

---

## 小技巧

1. **使用引号**: 用引号包裹方法名/类名可提高识别准确度
   - 例: "谁调用了 `createOrder`？"

2. **指定范围**: 在问题中指定项目或模块
   - 例: "订单模块中谁调用了 createOrder？"

3. **组合查询**: 可以组合多个条件
   - 例: "张三在上周提交的 createOrder 相关代码"

4. **来源跳转**: 点击回答中的文件链接可跳转到代码位置

---

## 常见问题

### Q: 解析失败怎么办？

检查：
1. Git 地址是否正确
2. 分支是否存在
3. 是否有访问权限

### Q: 问答结果不准确？

可能原因：
1. 项目未完成解析
2. 代码结构发生变化但未同步
3. 方法名不够具体（有重名）

解决方案：
1. 确认解析状态为「完成」
2. 触发增量解析同步最新代码
3. 使用更完整的方法名（如 `OrderService.createOrder`）

### Q: 如何查看历史对话？

对话历史会自动保存在左侧的「历史对话」列表中，点击可继续。

---

如有其他问题，请联系管理员或在 GitHub 提交 Issue。