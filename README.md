# Webhook Delivery Platform

> Durable, signed, and observable webhook delivery.  
> 持久化、可签名、可观测的 Webhook 投递平台。

[English](#english) · [中文](#中文)

## English

Webhook Delivery Platform is a production-style full-stack reference implementation for reliable outbound events. It accepts an event once, persists a delivery job in PostgreSQL, signs the exact request body, and records every delivery outcome for review and replay.

The first release intentionally uses a modular monolith and a PostgreSQL-backed queue. This keeps the consistency boundary explicit while still supporting multiple workers through `FOR UPDATE SKIP LOCKED` leases.

### Engineering evidence

- Java 21, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL, and Flyway
- React 19, strict TypeScript, Vite, responsive operations console, and SSE updates
- At-least-once delivery with idempotent event acceptance
- HMAC-SHA256 signatures over timestamp and exact request body
- Exponential backoff, bounded attempts, dead letters, and manual replay
- AES-256-GCM encryption for endpoint signing secrets
- URL validation, public-address policy, port allowlist, no redirects, and bounded responses
- Database leases that recover abandoned `PROCESSING` jobs
- Bounded virtual-thread execution so jobs are leased only when outbound capacity is available
- Unit tests, architecture verification, PostgreSQL integration test, frontend tests, and GitHub Actions
- Docker Compose demo with a signature-verifying receiver
- Delivery-attempt detail API and bilingual timeline for committed outcomes
- After-commit Micrometer attempt counters, duration timers, and structured completion logs
- Controlled transient-failure receiver for demonstrating retry recovery
- Reversible Endpoint activation with optimistic version checks and stable conflict responses
- Prometheus queue-health gauges with bounded status tags and runnable-job age
- Race-safe, idempotent cancellation for queued delivery jobs
- Commit-consistent SSE state notifications and after-commit operator action logs
- Immutable per-delivery target URL and encrypted-secret snapshots across retries and replay
- Versioned Endpoint signing-secret rotation without exposing secret material
- Versioned Endpoint name and target-URL editing with full safety revalidation
- Native single-operator sessions, CSRF-protected mutations, and stable access errors
- Bounded login throttling and fixed-cardinality authentication outcome metrics

### Quick start

Requirements: Docker with Compose.

```bash
cp .env.example .env
docker compose up --build
```

Open [http://localhost:8088](http://localhost:8088).

Sign in with the development-only Compose defaults: `admin` / `local-admin-password`. Override `APP_OPERATOR_USERNAME` and `APP_OPERATOR_PASSWORD` through `.env` for any non-local deployment. When the public origin uses HTTPS, also set `APP_OPERATOR_COOKIE_SECURE=true`.

The console is pre-filled for the included receiver:

- URL: `http://receiver:8090/hooks`
- Signing secret: `local-demo-secret`

Register the endpoint, publish the sample event, and watch the job move from `PENDING` to `SUCCEEDED`.

To exercise retry recovery, register another endpoint with this URL:

```text
http://receiver:8090/hooks/flaky?failures=2
```

The receiver returns HTTP `503` twice for each event and then succeeds. Use **Inspect** to review the committed attempt timeline. Runtime metrics include `/actuator/metrics/webhook.delivery.attempts`, `/actuator/metrics/webhook.delivery.duration`, and `/actuator/metrics/webhook.operator.authentication`.

For a reproducible Prometheus view, start the optional observability profile:

```bash
docker compose --profile observability up --build
```

Open [http://localhost:9090/targets](http://localhost:9090/targets) and query `webhook_delivery_jobs`, `webhook_delivery_oldest_runnable_age_seconds`, or `webhook_operator_authentication_total`. The Prometheus port is bound to `127.0.0.1`; the frontend proxies only health checks, not metrics. In production, keep all management endpoints on a private operations network.

### Delivery contract

Every request contains:

```text
Content-Type: application/json
X-Webhook-Id: <event UUID>
X-Webhook-Type: <event type>
X-Webhook-Timestamp: <Unix seconds>
X-Webhook-Signature: v1=<HMAC-SHA256>
```

The signed message is:

```text
<timestamp>.<exact request body>
```

Consumers should reject stale timestamps and compare signatures in constant time. The included demo receiver implements constant-time verification.

### Quality checks

```bash
./scripts/check.sh
```

The backend gate also checks Google Java Format and verifies the Spring Modulith dependency graph. The PostgreSQL integration test starts an actual target server, confirms idempotent acceptance, waits for the scheduled worker, and cryptographically verifies a successful signed delivery.

### Documentation

- [Architecture](docs/architecture.md)
- [Security model](docs/security.md)
- [Security reporting](SECURITY.md)
- [OpenAPI contract](docs/openapi.yaml)
- [API compatibility policy](docs/api-compatibility.md)
- [Upgrade, backup, and recovery](docs/operations-recovery.md)
- [v1.0 production readiness](docs/production-readiness.md)
- [ADR-0001: PostgreSQL-backed delivery queue](docs/adr/0001-postgresql-delivery-queue.md)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)

### Current scope

`v1.0` development stabilizes the existing single-operator HTTP boundary without expanding it into an identity platform. The documented routes, schemas, error codes, request-correlation behavior, and delivery SSE events follow the published 1.x compatibility policy. Multi-user accounts, distributed rate limiting, roles, tenant isolation, and external identity providers remain outside this release.

## 中文

Webhook Delivery Platform 是一个生产风格的全栈可靠事件投递参考实现。系统接收事件后，先在 PostgreSQL 中持久化投递任务，再对完整请求体签名，并记录每次投递结果，支持审查和重新投递。

首个版本采用模块化单体与 PostgreSQL 队列，在保持事务边界清晰的同时，通过 `FOR UPDATE SKIP LOCKED` 租约支持多个 Worker 协同处理。

### 工程能力

- Java 21、Spring Boot 4.1、Spring Modulith 2.1、PostgreSQL 与 Flyway
- React 19、严格 TypeScript、Vite、响应式运维控制台与 SSE 更新
- At-least-once 投递语义与幂等事件接收
- 基于时间戳和完整请求体的 HMAC-SHA256 签名
- 指数退避、有限尝试、死信状态和手动重投
- 使用 AES-256-GCM 加密 Endpoint 签名密钥
- URL 校验、公网地址策略、端口白名单、禁止重定向和响应大小限制
- 数据库任务租约，以及异常中断后对 `PROCESSING` 任务的恢复
- 有并发上限的虚拟线程执行器，只在存在出站容量时抢占任务
- 单元测试、模块架构验证、PostgreSQL 集成测试、前端测试和 GitHub Actions
- 包含签名验证 Receiver 的 Docker Compose 演示环境
- 投递详情 API，以及展示已提交结果的中英双语尝试时间线
- 事务提交后记录的 Micrometer 尝试计数、耗时指标与结构化完成日志
- 用于演示重试恢复的可控瞬时失败 Receiver
- 支持乐观版本校验与稳定冲突响应的可逆 Endpoint 启停控制
- 使用固定状态标签与可运行任务年龄的 Prometheus 队列健康指标
- 支持并发安全和幂等操作的排队任务取消能力
- 只反映已提交状态的 SSE 通知，以及提交后的人工操作日志
- 在重试与重投期间保持不变的任务级目标 URL 与加密密钥快照
- 不暴露密钥内容且带版本校验的 Endpoint 签名密钥轮换
- 重新执行完整安全校验且带版本控制的 Endpoint 名称与目标地址编辑
- 原生单操作者会话、带 CSRF 防护的写请求，以及稳定访问错误结构
- 有界登录限流与固定基数认证结果指标

### 快速开始

环境要求：Docker 与 Docker Compose。

```bash
cp .env.example .env
docker compose up --build
```

访问 [http://localhost:8088](http://localhost:8088)。

使用 Compose 中仅供开发的默认凭据登录：`admin` / `local-admin-password`。任何非本地部署都必须通过 `.env` 覆盖 `APP_OPERATOR_USERNAME` 与 `APP_OPERATOR_PASSWORD`；公网入口启用 HTTPS 后，还应设置 `APP_OPERATOR_COOKIE_SECURE=true`。

控制台已经预填演示 Receiver 信息：

- 地址：`http://receiver:8090/hooks`
- 签名密钥：`local-demo-secret`

如需验证重试恢复，可再注册以下 Endpoint：

```text
http://receiver:8090/hooks/flaky?failures=2
```

Receiver 会针对每个事件先返回两次 HTTP `503`，随后成功。可通过“查看详情”审查已提交的尝试时间线；运行时指标包括 `/actuator/metrics/webhook.delivery.attempts`、`/actuator/metrics/webhook.delivery.duration` 与 `/actuator/metrics/webhook.operator.authentication`。

如需使用可复现的 Prometheus 视图，可启动可选的可观测性 Profile：

```bash
docker compose --profile observability up --build
```

访问 [http://localhost:9090/targets](http://localhost:9090/targets)，可查询 `webhook_delivery_jobs`、`webhook_delivery_oldest_runnable_age_seconds` 或 `webhook_operator_authentication_total`。Prometheus 端口仅绑定 `127.0.0.1`，前端也只代理健康检查而不会公开指标。生产环境必须将所有管理端点限制在私有运维网络。

注册 Endpoint 并发布示例事件后，可以观察任务从 `PENDING` 进入 `SUCCEEDED`。

### 投递协议

每个请求包含以下 Header：

```text
Content-Type: application/json
X-Webhook-Id: <事件 UUID>
X-Webhook-Type: <事件类型>
X-Webhook-Timestamp: <Unix 秒>
X-Webhook-Signature: v1=<HMAC-SHA256>
```

签名原文为：

```text
<timestamp>.<完整请求体>
```

接收方应拒绝过期时间戳，并使用常量时间算法比较签名。仓库内的 Demo Receiver 已实现常量时间校验。

### 质量检查

```bash
cd backend && mvn -B verify
cd frontend && npm ci && npm run check
```

PostgreSQL 集成测试会启动真实目标服务，通过 API 验证幂等接收，等待定时 Worker 处理，并对成功投递执行真实签名校验。

后端质量门禁还会检查 Google Java Format，并验证 Spring Modulith 依赖图不存在循环依赖。

### 项目文档

- [架构说明](docs/architecture.md)
- [安全模型](docs/security.md)
- [安全问题报告](SECURITY.md)
- [OpenAPI 契约](docs/openapi.yaml)
- [API 兼容性政策](docs/api-compatibility.md)
- [升级、备份与恢复](docs/operations-recovery.md)
- [v1.0 生产就绪基线](docs/production-readiness.md)
- [ADR-0001：PostgreSQL 投递队列](docs/adr/0001-postgresql-delivery-queue.md)
- [贡献指南](CONTRIBUTING.md)
- [变更记录](CHANGELOG.md)

### 当前范围

`v1.0` 开发阶段将稳定现有单操作者 HTTP 边界，而不会把它扩展为身份平台。已记录的路由、Schema、错误码、请求关联行为与投递 SSE 事件遵循公开的 1.x 兼容性政策。多人账号、分布式限流、角色、租户隔离与外部身份提供方仍不在本版本范围内。

## License

[MIT](LICENSE) © 2026 NCC
