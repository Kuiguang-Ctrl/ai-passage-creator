# AI 爆款文章创作器

基于 **多智能体（Multi-Agent）** 架构的 AI 图文文章创作平台。用户只需输入一个选题，系统自动完成 **标题生成 → 大纲规划 → 正文撰写 → 配图生成 → 图文合成** 的全流程，并在每个关键节点让用户参与决策。

后端使用 **Spring AI Alibaba StateGraph** 做图编排，前端使用 **Vue 3 + Vite**，生成过程通过 **SSE** 实时推送。

## 核心流程

```
① 输入选题
      ↓
② 生成 3-5 套标题方案   →  用户选择并补充描述
      ↓
③ 流式生成结构化大纲     →  用户确认 / AI 辅助修改
      ↓
④ 生成正文 → 分析配图需求 → 并行生成配图 → 图文合成
      ↓
⑤ 输出完整图文文章
```

## 功能特性

- **五大智能体分工**：标题生成、大纲生成、正文生成、配图分析、图文合成各司其职，Prompt 模板集中管理
- **人机协同**：三阶段拆分，前一阶段确认后才进入下一阶段，避免"一次性生成不可控"
- **实时流式输出**：大纲与正文逐字推送，前端即时渲染
- **双通道配图**：Pexels 图库检索 + Nano Banana（Gemini）AI 生图，可按需配置启用策略
- **多种图元**：Mermaid 图表、Iconify 图标、SVG 概念示意图、表情包检索
- **异步 + SSE**：生成任务异步执行，失败不影响主请求；凭 taskId 可恢复进度
- **用户体系**：注册登录、Redis Session、配额扣减、Stripe 支付

## 技术栈

**后端**

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 3.5.9 | 基础框架 |
| Java | 21 | 运行环境 |
| Spring AI Alibaba | 1.1.0.0-RC2 | 通义千问接入 + StateGraph 编排 |
| MyBatis-Flex | 1.11.1 | ORM |
| Redis + Redisson | 3.50.0 | Session 与分布式支持 |
| Knife4j | 4.4.0 | 接口文档 |

**前端**

| 技术 | 版本 |
| --- | --- |
| Vue | 3.5 |
| Vite | 7 |
| TypeScript | 5.8 |
| Ant Design Vue | 4.2 |
| Pinia / Vue Router | 3 / 4 |

**外部服务与中间件**：通义千问（DashScope）、腾讯云 COS、Pexels API、Nano Banana API、Stripe、MySQL、Redis

## 项目结构

```
ai-passage-creator
├── src/main/java/com/ysw/aipassagecreator
│   ├── agent/          # 多智能体核心：编排器、5 个 Agent、并行配图、流式上下文
│   ├── controller/     # REST + SSE 接口
│   ├── service/        # 业务逻辑
│   ├── manager/        # SSE 连接管理
│   ├── config/         # 线程池、CORS、COS、Stripe 等配置
│   ├── aop/            # Agent 执行日志切面、权限拦截
│   ├── constant/       # Prompt 模板与常量
│   ├── mapper/         # MyBatis-Flex 数据访问
│   └── model/          # 实体、DTO、VO、枚举
├── src/main/resources
│   ├── application.yml                 # 主配置（密钥使用环境变量占位）
│   ├── application-local.yml.example   # 本地配置模板
│   └── application-prod.yml            # 生产配置（密钥使用环境变量占位）
└── frontend/           # Vue 3 + Vite 前端
```

## 快速开始

### 环境要求

JDK 21、MySQL 8.x、Redis、Node.js 20+、Maven 3.9+（或直接使用项目自带的 `mvnw`）

### 1. 初始化数据库

```sql
CREATE DATABASE ai_passage_creator DEFAULT CHARACTER SET utf8mb4;
```

表结构对应 `src/main/java/com/ysw/aipassagecreator/model/entity` 下的实体：`user`、`article`、`agent_log`、`payment_record`。

### 2. 配置密钥（重要）

仓库中不包含任何明文密钥，所有敏感配置均以 `${ENV:默认值}` 形式占位。

复制模板并填入真实值：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

该文件已被 `.gitignore` 排除，不会被提交。也可以用环境变量注入，见下表。

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

- 服务地址：http://localhost:8567/api
- 接口文档（Knife4j）：http://localhost:8567/api/doc.html

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：http://localhost:5173 ，开发服务器已把 `/api` 代理到后端 `8567` 端口。

## 环境变量一览

| 变量 | 说明 | 必需 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 通义千问 API Key | 是 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号密码 | 是 |
| `PEXELS_API_KEY` | Pexels 图库 API Key | 配图功能需要 |
| `REDIS_PASSWORD` | Redis 密码 | 可选 |
| `TENCENT_COS_SECRET_ID` / `TENCENT_COS_SECRET_KEY` | 腾讯云 COS 对象存储 | 可选 |
| `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe 支付 | 可选 |
| `APP_BASE_URL` | 支付回调地址 | 可选 |
| `KNIFE4J_PASSWORD` | 生产环境接口文档登录密码 | 可选 |

## SSE 消息类型

| 消息 | 说明 |
| --- | --- |
| `AGENT1_COMPLETE` | 标题 Agent 完成 |
| `AGENT2_STREAMING` / `AGENT2_COMPLETE` | 大纲流式片段 / 完成 |
| `AGENT3_STREAMING` / `AGENT3_COMPLETE` | 正文流式片段 / 完成 |
| `AGENT4_COMPLETE` | 配图需求分析完成 |
| `IMAGE_COMPLETE` | 单张配图生成完成（含图片 URL） |
| `AGENT5_COMPLETE` | 配图全部生成完成 |
| `MERGE_COMPLETE` | 图文合成完成 |
| `TITLES_GENERATED` / `OUTLINE_GENERATED` | 阶段一 / 阶段二完成 |
| `ALL_COMPLETE` / `ERROR` | 全部完成 / 错误 |

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/article/create` | 创建任务，进入阶段一（生成标题） |
| POST | `/article/confirm-title` | 选定标题，进入阶段二（生成大纲） |
| POST | `/article/confirm-outline` | 确认大纲，进入阶段三（正文 + 配图） |
| GET | `/article/progress/{taskId}` | 建立 SSE 连接，接收实时进度 |
| GET | `/article/{taskId}` | 查询文章详情 |
| POST | `/article/list` | 分页查询文章列表 |
| POST | `/article/delete` | 删除文章 |
| GET | `/article/execution-logs/{taskId}` | 查询 Agent 执行日志 |

## 说明

本项目为个人学习与实战练习作品，用于记录多智能体编排、异步任务与 SSE 实时推送的工程实践。
