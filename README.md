# Webhook Delivery Platform

Send events to HTTP endpoints, retry failures, and inspect delivery history.  
将事件推送到 HTTP 接口，自动重试失败请求，并查看投递记录。

[English](#english) · [中文](#中文)

## English

Webhook Delivery Platform accepts events through an API and sends them to registered HTTP endpoints. Jobs are stored in PostgreSQL so a worker can pick them up after a restart. Each request is signed, and the React console shows the result of each delivery attempt.

Built with Java 21, Spring Boot, PostgreSQL, and React. It uses a PostgreSQL queue with `FOR UPDATE SKIP LOCKED` leases to coordinate workers.

### Features

- **Delivery:** idempotent event acceptance, automatic retries with exponential backoff, dead-letter status, cancellation, and manual replay. Delivery is at-least-once, so receivers must deduplicate events by `X-Webhook-Id`.
- **Endpoint management:** edit names and URLs, pause endpoints, and rotate signing secrets. Version checks detect conflicting updates. Existing jobs keep their original URL and secret snapshot.
- **Request security:** HMAC-SHA256 signatures, encrypted signing secrets, and checks on destination URLs, addresses, ports, redirects, and response sizes. See the [security model](docs/security.md) for deployment requirements.
- **Console and monitoring:** delivery history, live status updates through SSE, Prometheus metrics, and operator action logs. Status notifications and attempt metrics are emitted after the database transaction commits.
- **Operator access:** one configured operator account, cookie sessions, CSRF protection, and login throttling.
- **Local demo:** Docker Compose starts the app, database, and a receiver that verifies signatures and can simulate failed requests.

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

[Retry walkthrough and runnable example](docs/demo.md)

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

### Development checks

```bash
./scripts/check.sh
```

The backend checks also verify Java formatting and the Spring Modulith dependency graph. The PostgreSQL integration test starts an actual target server, confirms idempotent acceptance, waits for the scheduled worker, and cryptographically verifies a successful signed delivery.

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

The latest release is [v1.0.0](https://github.com/TolkmisLK/webhook-delivery-platform/releases/tag/v1.0.0). It supports one configured operator account. Multi-user accounts, roles, tenant isolation, external identity providers, and distributed rate limiting are not implemented.

The HTTP API and SSE events follow the [1.x compatibility policy](docs/api-compatibility.md). Before deploying beyond the local demo, follow the [deployment checklist](docs/production-readiness.md) and [backup and recovery guide](docs/operations-recovery.md).

## 中文

Webhook Delivery Platform 通过 API 接收事件，再发送到已注册的 HTTP 接口。任务保存在 PostgreSQL 中，服务重启后可由 Worker 继续处理。每个请求都会签名，React 控制台可以查看每次投递的结果。

项目使用 Java 21、Spring Boot、PostgreSQL 和 React。队列直接使用 PostgreSQL，通过 `FOR UPDATE SKIP LOCKED` 和任务租约协调多个 Worker。

### 主要功能

- **事件投递：** 幂等接收、指数退避重试、死信状态、任务取消和手动重投。采用至少一次（at-least-once）投递语义，接收方需要按 `X-Webhook-Id` 去重。
- **接收端管理：** 修改名称和地址、暂停接收端、轮换签名密钥。版本校验用于识别并发修改；已有任务继续使用创建时的地址和密钥快照。
- **请求安全：** HMAC-SHA256 签名、签名密钥加密，以及目标 URL、地址、端口、重定向和响应大小检查。部署要求见[安全模型](docs/security.md)。
- **控制台与监控：** 投递历史、SSE 实时状态、Prometheus 指标和人工操作日志。状态通知和尝试指标在数据库事务提交后发出。
- **登录管理：** 一个预设操作者账号、Cookie 会话、CSRF 防护和登录限流。
- **本地演示：** Docker Compose 启动应用、数据库和接收服务；接收服务支持验签和模拟请求失败。

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

Receiver 会针对每个事件先返回两次 HTTP `503`，随后成功。可通过“查看详情”查看每次请求的结果和耗时；运行时指标包括 `/actuator/metrics/webhook.delivery.attempts`、`/actuator/metrics/webhook.delivery.duration` 与 `/actuator/metrics/webhook.operator.authentication`。

如需查看 Prometheus 指标，可启用 Compose 的 `observability` 配置：

```bash
docker compose --profile observability up --build
```

访问 [http://localhost:9090/targets](http://localhost:9090/targets)，可查询 `webhook_delivery_jobs`、`webhook_delivery_oldest_runnable_age_seconds` 或 `webhook_operator_authentication_total`。Prometheus 端口仅绑定 `127.0.0.1`，前端也只代理健康检查而不会公开指标。生产环境必须将所有管理端点限制在私有运维网络。

注册 Endpoint 并发布示例事件后，可以观察任务从 `PENDING` 进入 `SUCCEEDED`。

[一步步运行重试演示](docs/demo.md)

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

### 开发检查

```bash
./scripts/check.sh
```

PostgreSQL 集成测试会启动真实目标服务，通过 API 验证幂等接收，等待定时 Worker 处理，并对成功投递执行真实签名校验。

后端检查还包括 Google Java Format，并验证 Spring Modulith 依赖图不存在循环依赖。

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

最新版本为 [v1.0.0](https://github.com/TolkmisLK/webhook-delivery-platform/releases/tag/v1.0.0)，支持一个预设操作者账号。目前没有实现多人账号、角色、租户隔离、外部身份提供方和分布式限流。

HTTP API 和 SSE 事件遵循 [1.x 兼容性政策](docs/api-compatibility.md)。在本地演示以外的环境部署前，请按照[部署检查清单](docs/production-readiness.md)和[备份恢复说明](docs/operations-recovery.md)完成配置。

## License

[MIT](LICENSE) © 2026 NCC
