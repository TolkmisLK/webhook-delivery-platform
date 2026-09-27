# Changelog / 变更记录

All notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and semantic versioning.

重要变更遵循 Keep a Changelog 与语义化版本规范。

## [Unreleased]

## [1.0.0] - 2026-08-27

### Changed / 变更

- Aligned the backend, frontend, and OpenAPI release versions at 1.0.0.
  后端、前端与 OpenAPI 的发布版本统一为 1.0.0。
- Published the bilingual 1.x HTTP API compatibility and deprecation policy.
  发布双语的 1.x HTTP API 兼容与弃用政策。
- Completed protected-operation access errors, request-correlation semantics, and delivery SSE documentation in OpenAPI.
  在 OpenAPI 中补齐受保护操作的访问错误、请求关联语义和投递 SSE 文档。
- Published a bilingual upgrade, PostgreSQL backup/restore, strict rollback, and recovery-verification runbook.
  发布双语的升级、PostgreSQL 备份恢复、严格回滚和恢复验证手册。
- Published the bilingual v1.0 security/reliability evidence matrix, residual-risk register, and production checklist.
  发布双语的 v1.0 安全与可靠性证据矩阵、剩余风险清单和生产部署检查表。
- Added PostgreSQL integration coverage for reclaiming expired worker leases without changing delivery snapshots.
  增加 PostgreSQL 集成测试，验证过期 Worker 租约可重新抢占且投递快照不变。
- Added regression coverage that rejects encrypted endpoint secrets when the deployment master key is wrong.
  增加回归测试，验证部署主密钥错误时不能解密 Endpoint 签名密钥。

## [0.5.0] - 2026-08-27

### Added / 新增

- Native single-operator session authentication with deployment-provided credentials.
  增加使用部署侧凭据的原生单操作者会话认证。
- CSRF protection for state-changing API requests, including session-bound token rotation.
  对修改状态的 API 请求增加 CSRF 防护，包括与会话绑定的 Token 轮换。
- Stable JSON authentication and authorization errors with request correlation IDs.
  增加带请求关联 ID 的稳定 JSON 认证与授权错误响应。
- Bilingual sign-in, signed-in identity, and sign-out controls in the operations console.
  运维控制台增加双语登录、当前身份显示和登出功能。
- Bounded per-client and process-wide login throttling with stable HTTP 429 responses.
  增加单客户端及单进程总量的登录限流，超额时返回稳定的 HTTP 429 响应。
- Fixed-cardinality authentication outcome metrics and metadata-only security logs.
  增加固定基数的认证结果指标，以及只记录元数据的安全日志。
## [0.4.0] - 2026-08-27

### Added / 新增

- Race-safe, idempotent cancellation for queued delivery jobs with a stable conflict response.
  增加可应对并发、具备幂等性的排队任务取消操作，并提供稳定的冲突响应。
- Bilingual cancellation controls and bounded Prometheus coverage for the `CANCELED` state.
  增加双语取消控件，并将 `CANCELED` 状态纳入有界 Prometheus 指标。
- Commit-consistent delivery SSE notifications and after-commit operator action logs.
  增加与事务提交一致的投递 SSE 通知，以及提交后产生的操作者操作日志。
- Immutable target URL and encrypted signing-secret snapshots for every accepted delivery.
  每项已接收任务保存不可变的目标 URL 和加密签名密钥快照。
- Versioned Endpoint signing-secret rotation with metadata-only after-commit audit logs.
  增加带版本校验的 Endpoint 签名密钥轮换，以及仅含元数据的提交后审计日志。
- Bilingual secret-rotation controls and old/new delivery-signature integration coverage.
  增加双语密钥轮换控件，并通过集成测试覆盖轮换前后任务的签名。
- Versioned Endpoint name and target-URL editing with complete URL-safety revalidation.
  增加带版本校验的 Endpoint 名称与目标 URL 编辑，并重新执行完整 URL 安全校验。
- Bilingual configuration controls and old/new delivery-target integration coverage.
  增加双语配置控件，并通过集成测试覆盖编辑前后任务的目标地址。

## [0.3.0] - 2026-08-25

### Added / 新增

- Versioned Endpoint activation and deactivation with stable HTTP 409 conflict responses.
  增加带版本校验的 Endpoint 启停操作，并在冲突时返回稳定的 HTTP 409 响应。
- Bilingual operator controls that publish events only to active Endpoints.
  增加双语操作者控件，仅允许向启用的 Endpoint 发布事件。
- After-commit structured lifecycle logs for Endpoint status changes.
  Endpoint 状态变化后增加事务提交后的结构化生命周期日志。
- Prometheus exposition with bounded delivery-status gauges and oldest-runnable-job age.
  增加 Prometheus 指标，包括有界的投递状态 Gauge 与最早可运行任务的等待时长。
- Reproducible, localhost-bound Prometheus Compose profile.
  增加可复现且仅绑定 localhost 的 Prometheus Compose 配置。

## [0.2.0] - 2026-08-25

### Added / 新增

- Delivery detail API and bilingual committed-attempt timeline.
  增加投递详情 API 和双语的已提交尝试时间线。
- After-commit Micrometer counters, duration timers, and structured completion logs.
  增加事务提交后的 Micrometer 计数器、耗时计时器和结构化完成日志。
- Controlled transient-failure receiver and retry-recovery integration coverage.
  增加可控的瞬时失败接收端，并以集成测试覆盖重试恢复。

## [0.1.0] - 2026-08-25

### Added / 新增

- Java 21 and Spring Boot 4.1 modular backend.
  建立 Java 21 与 Spring Boot 4.1 模块化后端。
- PostgreSQL event and delivery-job persistence with Flyway migrations.
  使用 PostgreSQL 持久化事件与投递任务，并通过 Flyway 管理迁移。
- HMAC-SHA256 delivery, AES-256-GCM secret storage, retries, leases, and dead letters.
  实现 HMAC-SHA256 投递签名、AES-256-GCM 密钥存储、重试、租约和死信状态。
- React and TypeScript operations console with SSE updates.
  建立 React 与 TypeScript 运维控制台，支持 SSE 状态更新。
- Docker Compose demo receiver, automated tests, CI, architecture, API, and security documentation.
  增加 Docker Compose 演示接收端、自动化测试、CI 及架构、API 和安全文档。
- Spring Modulith dependency-graph verification and Google Java Format enforcement.
  增加 Spring Modulith 依赖图验证和 Google Java Format 格式检查。
