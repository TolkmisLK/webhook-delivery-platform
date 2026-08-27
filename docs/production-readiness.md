# v1.0 production readiness / v1.0 生产就绪基线

[English](#english) · [中文](#中文)

This report records the security and reliability evidence reviewed for v1.0. It is a
release gate for the repository's existing single-operator deployment model, not a
certification or a substitute for infrastructure threat modelling.

本文记录 v1.0 已审查的安全与可靠性证据。它是仓库现有单操作者部署模型的发布门禁，
不是安全认证，也不能替代针对实际基础设施的威胁建模。

## English

### Decision and scope

The application is ready for a controlled v1.0 deployment when every item in the
production checklist is satisfied by the target environment. The code and automated
tests establish the application boundary; the operator remains responsible for TLS,
network ingress and egress, durable PostgreSQL operations, external secret storage,
monitoring, backup drills, and receiver behavior.

The review covers:

- native single-operator sessions, CSRF, stable access errors, and login throttling;
- SSRF controls, redirect handling, request/response bounds, and immutable targets;
- endpoint-secret encryption, rotation behavior, and metadata-only lifecycle logs;
- PostgreSQL acceptance, leases, retries, restart recovery, and at-least-once delivery;
- bounded metrics, request correlation, dependency gates, and operational recovery.

Multi-user identity, roles, tenant isolation, distributed sessions or login quotas,
managed point-in-time recovery, and network-policy automation are outside v1.0.

### Control-to-evidence matrix

| Area | v1.0 claim | Automated or source evidence | Deployment evidence required |
| --- | --- | --- | --- |
| Authentication | Credentials come from deployment configuration; BCrypt verifies them; successful login rotates the session; logout invalidates it | `OperatorAuthWebTest`, `OperatorSecurityConfiguration`, `OperatorAuthController` | Unique 16+ character password in a secret store; no demo credential |
| CSRF and access errors | Every unsafe API operation requires a session token; unauthenticated and denied requests use stable JSON errors and `X-Request-Id` | `OperatorAuthWebTest`, OpenAPI `UnauthorizedError` and `ForbiddenError` | One HTTPS origin; reverse proxy preserves the response header and does not cache token responses |
| Login abuse | Per-client and process-wide quotas reject before BCrypt with HTTP 429 and `Retry-After`; metrics have a fixed outcome set | `OperatorLoginAttemptLimiterTest`, `OperatorLoginRateLimitWebTest`, `OperatorAuthTelemetryTest` | Edge rate limit for public ingress; alert on failure/rate-limited changes |
| Authentication logs | Login and logout logs contain outcome/client metadata, not passwords, CSRF tokens, or session IDs | `OperatorAuthController`, `OperatorAuthTelemetryTest`; manual source review | Central log access control and retention policy |
| SSRF | Only HTTP(S), allowed ports, and public resolved addresses are accepted by default; URL policy runs at registration, update, and immediately before delivery | `UrlSafetyPolicyTest`, `EndpointServiceTest`, `DeliveryContextService` | Keep private targets disabled; enforce egress policy or a validating outbound proxy |
| Redirect and bounds | The client never follows redirects; connect/request timeout, response excerpt, retry count, and worker concurrency are bounded | `ApplicationConfiguration`, `WebhookHttpClient`, `DeliveryPropertiesTest` | Reverse-proxy request-body limit and capacity values sized for the environment |
| Secrets at rest | Endpoint secrets use AES-256-GCM with a fresh IV; API responses and audit events omit secret material | `SecretCipherTest`, `EndpointServiceTest`, `WebhookFlowIT` rotation regression | Stable 32-byte master key in a secret store; restricted database and backup access |
| Key mismatch | Ciphertext cannot be decrypted with a replacement key; changing the key without migration makes deliveries fail safely | `SecretCipherTest.rejectsCiphertextEncryptedWithAnotherMasterKey` | Restore drill proves the database and matching key are recovered together |
| Immutable delivery context | Accepted jobs retain their target URL and encrypted-secret snapshot across endpoint edits, rotation, retries, replay, and lease reclaim | `WebhookFlowIT` snapshot, configuration, rotation, and expired-lease regressions | Receiver overlap window during secret rotation |
| Durable acceptance | Event and delivery job are committed in PostgreSQL before HTTP 202; idempotency returns the original job | `WebhookFlowIT.acceptsSignsAndDeliversAnEvent`, schema uniqueness constraint | PostgreSQL durability, storage monitoring, and tested backups |
| Worker recovery | Expired `PROCESSING` leases are reclaimable; an abandoned job reaches `SUCCEEDED` with one committed attempt after restart-style recovery | `WebhookFlowIT.reclaimsAnExpiredProcessingLeaseWithoutChangingTheDeliverySnapshot`, `DeliveryStateEventsTest` | Unique worker IDs, lease timeout longer than normal requests, clock synchronization |
| Delivery semantics | Retries are bounded and persisted; transient failures recover; terminal failures remain reviewable and replayable | `RetryPolicyTest`, `DeliveryJobTest`, `WebhookFlowIT.exposesTheAttemptTimelineAfterTransientFailures` | Receiver verifies signature/timestamp and deduplicates `X-Webhook-Id` |
| Observability | Status labels and authentication outcomes are bounded; lifecycle logs are emitted after commit; requests carry correlation IDs | `QueueHealthMetricsTest`, `DeliveryTelemetryTest`, `DeliveryStateEventsTest`, `OperatorAuthWebTest` | Private metrics network, dashboards, paging thresholds, and log correlation |
| API contract | OpenAPI documents protected operations, stable errors, correlation, and SSE payloads and is linted in CI | `docs/openapi.yaml`, Redocly CI, frontend API tests | Clients pinned to the published 1.x compatibility policy |
| Dependencies and build | Maven resolves a Spring-managed graph on Java 21; frontend production dependencies are audited at high severity; lockfile and CI action major versions are committed | `mvn verify`, `npm ci`, `npm audit --omit=dev --audit-level=high`, `.github/workflows/ci.yml` | Review GitHub dependency alerts before release and on a maintained cadence |
| Recovery | Backup boundaries, checksums, clean restore, Flyway validation, strict rollback, and smoke tests are documented | `docs/operations-recovery.md` | Recorded restore drill meeting the declared RPO/RTO |

### Restart and delivery invariants

The database is the source of truth. A process can disappear after acquiring a lease,
after sending a request, or before persisting the response. On restart, a
`PENDING`/`RETRY_SCHEDULED` job remains runnable and an expired `PROCESSING` lease is
reclaimed. The target and encrypted secret are copied into the job at acceptance and
are not re-read from the endpoint row.

Recovery does not create exactly-once delivery. If the process stops after the receiver
accepts a request but before the success transaction commits, the same
`X-Webhook-Id` can be delivered again. Receiver-side idempotency is therefore a required
part of the contract, not an optional optimization.

Use a lease timeout comfortably longer than the configured request timeout and normal
scheduler delay. Give every concurrently running process a distinct worker ID. Monitor
stale locks and oldest runnable age; investigate them before shortening the lease.

### Residual risks and accepted boundaries

| Risk | v1.0 position | Required mitigation |
| --- | --- | --- |
| Duplicate delivery around crash/timeout | Accepted by the at-least-once contract | Receiver deduplication by `X-Webhook-Id`; idempotent side effects |
| DNS rebinding between validation and connection | Application revalidates DNS, but resolution and connection are not one atomic operation | Network egress allowlist or validating outbound proxy |
| Login quota and session loss on restart | Quotas and sessions are in process memory and are not shared by replicas | Edge rate limiting; single-console expectations; operator signs in again |
| Proxy address ambiguity | Application does not trust forwarded headers; a proxy may make clients share one address | Configure trusted forwarding at the container/edge boundary, never from arbitrary clients |
| Master-key rotation | No automatic re-encryption migration | Controlled old-key/new-key migration and verified backup before replacement |
| Database recovery point | Repository runbook uses logical backups and does not configure managed PITR | Infrastructure-specific WAL/PITR or snapshots if the declared RPO needs them |
| JVM vulnerability scanning | CI compiles/tests the resolved graph but does not run a dedicated CVE feed scanner | Review repository dependency alerts and upstream Spring advisories on every release |
| Payload sensitivity | Event bodies and response excerpts are operational data in PostgreSQL | Minimize payloads, restrict database/backups, set retention, sanitize receiver responses |
| Single operator | No per-person identity or attributable authorization | Restrict console access; do not share credentials where individual accountability is required |

### Production deployment checklist

Complete and record this checklist for each environment and release candidate.

#### Identity and ingress

- [ ] Serve one canonical origin over HTTPS; redirect or reject plaintext traffic.
- [ ] Set `APP_OPERATOR_COOKIE_SECURE=true` and verify the session cookie is HTTP-only and Secure.
- [ ] Replace all demo credentials with a unique operator password of at least 16 characters.
- [ ] Restrict console/API ingress to the intended operator network or identity-aware edge.
- [ ] Apply edge request-size and login-rate limits; configure trusted proxy handling explicitly.
- [ ] Confirm caches do not store login, CSRF, session, or authenticated API responses.

#### Secrets and database

- [ ] Generate a random Base64-encoded 32-byte `APP_SECURITY_MASTER_KEY`; store it outside Git and the database.
- [ ] Back up the matching master key, operator credentials, and deployment configuration separately from PostgreSQL.
- [ ] Use a dedicated least-privilege PostgreSQL role, encrypted transport when crossing hosts, and restricted backups.
- [ ] Run Flyway validation before traffic and never start an older binary against a newer schema.
- [ ] Record backup ownership, retention, RPO/RTO, last successful restore drill, and integrity checksum.

#### Delivery boundary

- [ ] Keep `APP_SECURITY_ALLOW_PRIVATE_TARGETS=false` and allow only required target ports.
- [ ] Enforce outbound DNS/IP/port policy outside the process; block cloud metadata and internal networks.
- [ ] Assign a unique `APP_DELIVERY_WORKER_ID` to every concurrent instance.
- [ ] Keep lease timeout above request timeout plus scheduler margin; size concurrency for downstream capacity.
- [ ] Require receivers to validate timestamp/signature in constant time and deduplicate `X-Webhook-Id`.
- [ ] Plan an old/new secret acceptance window for endpoint key rotation.

#### Operations and release

- [ ] Keep Actuator and Prometheus on a private operations network.
- [ ] Alert on runnable age, stale locks, dead jobs, retry rate, login failures/rate limits, database health, and disk pressure.
- [ ] Confirm logs and receiver excerpts contain no credentials, tokens, signing secrets, or unnecessary personal data.
- [ ] Run Java 21 backend/integration, frontend, OpenAPI, formatting, and dependency gates on the exact release commit.
- [ ] Execute the recovery runbook smoke tests in an isolated target before approving the release.
- [ ] Record rollback artifact, database compatibility decision, on-call owner, and release evidence URLs.

## 中文

### 结论与范围

目标环境满足本页全部生产清单后，应用可进入受控的 v1.0 部署。代码和自动化测试负责证明
应用边界；TLS、网络入口与出口、PostgreSQL 持久化运维、外部密钥存储、监控、恢复演练
以及 Receiver 行为仍由部署方负责。

本次回归覆盖：

- 原生单操作者会话、CSRF、稳定访问错误与登录限流；
- SSRF、重定向处理、请求/响应边界与不可变目标快照；
- Endpoint 密钥加密、轮换行为与仅含元数据的生命周期日志；
- PostgreSQL 接收、租约、重试、重启恢复与 at-least-once 投递；
- 固定基数指标、请求关联、依赖门禁与运维恢复。

多人身份、角色、租户隔离、分布式会话或登录配额、托管 PITR 与网络策略自动化不在
v1.0 范围内。

### 控制与证据矩阵

| 领域 | v1.0 结论 | 自动化或源码证据 | 部署侧必需证据 |
| --- | --- | --- | --- |
| 认证 | 凭据来自部署配置，BCrypt 校验；登录成功轮换 Session，登出使其失效 | `OperatorAuthWebTest`、`OperatorSecurityConfiguration`、`OperatorAuthController` | 密钥存储中的 16+ 字符唯一密码；不得使用演示凭据 |
| CSRF 与访问错误 | 所有不安全 API 操作需要 Session Token；401/403 使用稳定 JSON 与 `X-Request-Id` | `OperatorAuthWebTest`、OpenAPI 错误 Schema | 单一 HTTPS Origin；代理保留关联头且不缓存 Token 响应 |
| 登录滥用 | 按客户端和进程双层配额在 BCrypt 前返回 429/`Retry-After`；指标结果集合固定 | `OperatorLoginAttemptLimiterTest`、`OperatorLoginRateLimitWebTest`、`OperatorAuthTelemetryTest` | 公网入口边缘限流；失败/限流变化告警 |
| 认证日志 | 登录/登出日志只含结果和客户端元数据，不含密码、CSRF Token 或 Session ID | `OperatorAuthController`、`OperatorAuthTelemetryTest` 与源码审查 | 集中日志访问控制与保留策略 |
| SSRF | 默认只允许 HTTP(S)、白名单端口与解析后的公网地址；注册、编辑和投递前均执行策略 | `UrlSafetyPolicyTest`、`EndpointServiceTest`、`DeliveryContextService` | 禁止私网目标；使用出口策略或验证连接地址的代理 |
| 重定向与边界 | 客户端不跟随重定向；连接/请求超时、响应摘要、重试次数与 Worker 并发均有上限 | `ApplicationConfiguration`、`WebhookHttpClient`、`DeliveryPropertiesTest` | 反向代理 Payload 上限与按环境评估的容量参数 |
| 静态密钥 | Endpoint 密钥使用随机 IV 的 AES-256-GCM；API 与审计事件不含密钥内容 | `SecretCipherTest`、`EndpointServiceTest`、`WebhookFlowIT` 轮换回归 | 稳定的 32 字节主密钥；限制数据库与备份访问 |
| 主密钥不匹配 | 替换后的错误主密钥不能解密历史密文，未迁移换钥会安全失败 | `SecretCipherTest.rejectsCiphertextEncryptedWithAnotherMasterKey` | 恢复演练证明数据库与对应主密钥一起恢复 |
| 不可变投递上下文 | Endpoint 编辑、轮换、重试、重投与租约恢复都不改变已接收任务的目标和加密密钥快照 | `WebhookFlowIT` 快照、配置、轮换与过期租约回归 | 密钥轮换时 Receiver 同时接受新旧密钥的窗口 |
| 持久化接收 | 返回 HTTP 202 前，事件与任务已写入 PostgreSQL；幂等请求返回原任务 | `WebhookFlowIT.acceptsSignsAndDeliversAnEvent`、唯一约束 | PostgreSQL 持久化、存储监控与已验证备份 |
| Worker 恢复 | 过期 `PROCESSING` 租约可重新抢占；遗留任务使用原快照完成并只提交一次尝试 | `WebhookFlowIT.reclaimsAnExpiredProcessingLeaseWithoutChangingTheDeliverySnapshot`、`DeliveryStateEventsTest` | 唯一 Worker ID；租约长于正常请求；时钟同步 |
| 投递语义 | 重试次数有限且持久化；瞬时故障可恢复；终态故障可审查与重投 | `RetryPolicyTest`、`DeliveryJobTest`、瞬时失败集成回归 | Receiver 验签、校验时间戳并按 `X-Webhook-Id` 去重 |
| 可观测性 | 状态与认证标签集合有界；生命周期日志在事务提交后产生；请求携带关联 ID | `QueueHealthMetricsTest`、`DeliveryTelemetryTest`、`DeliveryStateEventsTest`、`OperatorAuthWebTest` | 私有指标网络、Dashboard、告警阈值与日志关联 |
| API 契约 | OpenAPI 记录受保护操作、稳定错误、关联头与 SSE Payload，并由 CI Lint | `docs/openapi.yaml`、Redocly CI、前端 API 测试 | 客户端遵循已发布的 1.x 兼容政策 |
| 依赖与构建 | Java 21 解析 Spring 管理的 Maven 依赖图；前端生产依赖执行高危审计；Lockfile 与 CI Action 主版本固定 | `mvn verify`、`npm ci`、`npm audit --omit=dev --audit-level=high`、CI Workflow | 发布前并定期审查 GitHub 依赖告警 |
| 恢复 | 已记录备份边界、校验和、干净恢复、Flyway 校验、严格回滚与冒烟测试 | `docs/operations-recovery.md` | 留存满足 RPO/RTO 的恢复演练记录 |

### 重启与投递不变量

数据库是唯一事实来源。进程可能在取得租约后、发送请求后或写入结果前退出。重启后，
`PENDING`/`RETRY_SCHEDULED` 任务仍可运行，过期的 `PROCESSING` 租约可被重新抢占。
目标 URL 与加密密钥在接收时复制到任务中，之后不会从 Endpoint 行重新读取。

恢复不等于 exactly-once。如果 Receiver 已接收请求，但成功事务提交前进程退出，同一个
`X-Webhook-Id` 仍可能再次投递。因此 Receiver 去重是协议的必要组成部分，而不是可选优化。

租约超时应明显长于请求超时与正常调度延迟之和。并发进程必须使用不同 Worker ID，并监控
陈旧锁与最老可运行任务年龄；缩短租约前应先定位异常原因。

### 剩余风险与已接受边界

| 风险 | v1.0 立场 | 必需缓解措施 |
| --- | --- | --- |
| 崩溃/超时附近的重复投递 | at-least-once 契约明确接受 | Receiver 按 `X-Webhook-Id` 去重，副作用保持幂等 |
| 校验与连接之间的 DNS Rebinding | 应用会重新解析，但解析与连接不是原子操作 | 网络出口白名单或验证连接地址的 Outbound Proxy |
| 重启导致登录配额与 Session 丢失 | 状态位于进程内，副本间不共享 | 边缘限流；接受重新登录；遵守单控制台边界 |
| 代理地址歧义 | 应用不信任转发头，多个客户端可能共享代理地址 | 只在容器/边缘配置可信转发，绝不信任任意客户端 Header |
| 主密钥轮换 | 没有自动重加密迁移 | 换钥前备份，执行受控新旧密钥迁移并验证 |
| 数据库恢复点 | 仓库手册使用逻辑备份，不配置托管 PITR | 如 RPO 需要，由基础设施提供 WAL/PITR 或快照 |
| JVM 漏洞扫描 | CI 编译并测试解析后的依赖图，但没有专用 CVE Feed Scanner | 每次发布审查仓库依赖告警与 Spring 上游安全通告 |
| Payload 敏感性 | 事件正文与响应摘要作为运维数据存于 PostgreSQL | 数据最小化、限制数据库/备份、设置保留期、清理响应内容 |
| 单操作者 | 没有个人身份和可归属授权记录 | 限制控制台访问；需要个人问责时不得共享账号 |

### 生产部署清单

每个环境和候选发布都必须完成并留存以下清单。

#### 身份与入口

- [ ] 使用单一规范 HTTPS Origin，明文流量只允许重定向或拒绝。
- [ ] 设置 `APP_OPERATOR_COOKIE_SECURE=true`，确认 Session Cookie 同时为 HTTP-only 与 Secure。
- [ ] 替换全部演示凭据，使用至少 16 字符的唯一操作者密码。
- [ ] 将控制台/API 入口限制在指定运维网络或具备身份能力的边缘网关。
- [ ] 在边缘设置请求大小与登录限流，并显式配置可信代理行为。
- [ ] 确认缓存不会保存登录、CSRF、Session 或已认证 API 响应。

#### 密钥与数据库

- [ ] 生成随机 Base64 编码的 32 字节 `APP_SECURITY_MASTER_KEY`，存于 Git 和数据库之外。
- [ ] 将对应主密钥、操作者凭据与部署配置和 PostgreSQL 分开备份。
- [ ] 使用专用最小权限 PostgreSQL 角色；跨主机启用加密传输并限制备份访问。
- [ ] 接流量前执行 Flyway 校验，禁止旧二进制连接新 Schema。
- [ ] 记录备份责任人、保留期、RPO/RTO、最近恢复演练与完整性校验和。

#### 投递边界

- [ ] 保持 `APP_SECURITY_ALLOW_PRIVATE_TARGETS=false`，仅允许业务必需端口。
- [ ] 在进程外限制出站 DNS/IP/端口，阻断云元数据地址与内部网络。
- [ ] 为每个并发实例配置唯一 `APP_DELIVERY_WORKER_ID`。
- [ ] 租约超时大于请求超时加调度余量；按下游容量设置并发。
- [ ] Receiver 必须以常量时间校验时间戳/签名，并按 `X-Webhook-Id` 去重。
- [ ] Endpoint 换钥时规划 Receiver 同时接受新旧密钥的窗口。

#### 运维与发布

- [ ] Actuator 与 Prometheus 只允许私有运维网络访问。
- [ ] 对可运行任务年龄、陈旧锁、死信、重试率、登录失败/限流、数据库健康与磁盘压力告警。
- [ ] 确认日志与 Receiver 响应摘要不含凭据、Token、签名密钥或不必要个人数据。
- [ ] 在准确发布提交上执行 Java 21 后端/集成、前端、OpenAPI、格式与依赖门禁。
- [ ] 批准发布前，在隔离目标中执行恢复手册的冒烟测试。
- [ ] 记录回滚制品、数据库兼容性结论、值班负责人和发布证据链接。
