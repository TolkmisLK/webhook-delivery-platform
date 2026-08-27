# Security model / 安全模型

[English](#english) · [中文](#中文)

## English

### Protected assets

- Endpoint signing secrets
- Event payloads
- Delivery history and error context
- Internal network reachability
- Worker availability
- Operator session and administrative API access

### Controls

| Threat | Control |
| --- | --- |
| Secret disclosure at rest | AES-256-GCM with a deployment-provided master key |
| Secret disclosure through API | Endpoint responses never contain the signing secret |
| Secret disclosure during rotation | Write-only secret input, encrypted replacement, metadata-only after-commit audit event |
| SSRF to local infrastructure | HTTP(S)-only URLs, DNS resolution checks, private-address policy, and port allowlist |
| Redirect-based policy bypass | Redirect following is disabled |
| DNS changes after registration | URL policy is evaluated again immediately before delivery |
| Unsafe Endpoint reconfiguration | Every target-URL edit re-runs the complete registration-time URL policy before persistence |
| Resource exhaustion | Request timeout, response-size bound, payload limit at the reverse proxy, bounded outbound concurrency and retry count |
| Forged delivery | HMAC-SHA256 over timestamp and exact body |
| Timing attack in receiver | Included receiver uses constant-time signature comparison |
| Worker crash | Expired database leases are reclaimable |
| Duplicate processing | Stable event ID and documented at-least-once contract |
| Unauthorized console or API access | Deployment-provided operator credentials, BCrypt verification, and a server-side HTTP-only session |
| Login session fixation | Session identifier rotation after successful authentication |
| Cross-site request forgery | Session-bound CSRF token required for every unsafe API request |
| Online password guessing | Per-remote-address and process-wide quotas run before BCrypt verification |
| Credential or token disclosure in logs | Authentication logs contain outcome metadata only |

### Master key

`APP_SECURITY_MASTER_KEY` is a Base64-encoded 32-byte key. Rotate it through a controlled migration that decrypts each endpoint with the old key and encrypts it with the new key. Do not replace it without migrating existing ciphertext.

### Endpoint secret rotation

Endpoint signing-secret rotation requires the version last observed by the operator. A stale request returns HTTP `409`; successful rotation increments the Endpoint version and never returns or logs either secret. Accepted delivery jobs keep their encrypted-secret snapshots, so receivers must accept both old and new secrets until pre-rotation deliveries reach terminal states.

Endpoint target-URL edits require the same observed version and re-run the complete URL safety policy before persistence. Successful audit events contain only change flags, not the old or new URL. Accepted delivery jobs keep their original target snapshots, so an edit cannot redirect queued or retried work.

### Deployment boundary

The current release is a single-operator engineering console with native session authentication. Set `APP_OPERATOR_USERNAME` and a unique `APP_OPERATOR_PASSWORD` of at least 16 characters through the deployment secret store. The password is verified through BCrypt and is never persisted by the application.

The session cookie is HTTP-only and uses `SameSite=Lax`; set `APP_OPERATOR_COOKIE_SECURE=true` behind an HTTPS origin. Unsafe API methods also require the session-backed CSRF token returned through `GET /api/auth/csrf`. Successful login rotates the session identifier and the CSRF token. Logout invalidates the server-side security context and clears the session cookie.

Native authentication does not remove the network boundary: keep the console and API behind HTTPS and restrict management endpoints to a private operations network. Multi-user accounts, roles, teams, tenant isolation, password recovery, and external identity providers are outside v1.0.

### Login abuse resistance

Each process permits 8 attempts per remote address and 64 attempts in total during a one-minute window. Exceeding the client quota blocks that address for five minutes; exceeding the global quota blocks the process for one minute. Rejections occur before BCrypt, return HTTP `429 login_rate_limited`, and include `Retry-After`. At most 1,024 client entries are retained; successful authentication clears that client's state but not the global quota.

The policy is controlled by `APP_OPERATOR_LOGIN_CLIENT_MAX_ATTEMPTS`, `APP_OPERATOR_LOGIN_GLOBAL_MAX_ATTEMPTS`, `APP_OPERATOR_LOGIN_WINDOW`, `APP_OPERATOR_LOGIN_CLIENT_BLOCK_DURATION`, `APP_OPERATOR_LOGIN_GLOBAL_BLOCK_DURATION`, and `APP_OPERATOR_LOGIN_MAX_CLIENT_ENTRIES`. Metrics expose only `webhook_operator_authentication_total{outcome}` with `success`, `failure`, `rate_limited`, and `logout`. A useful investigation query is `sum by (outcome) (rate(webhook_operator_authentication_total[5m]))`.

The limiter is intentionally in-memory and per process. It keys directly on the servlet remote address and does not trust forwarded headers. Behind a reverse proxy, all requests may therefore share the proxy address unless the deployment supplies a trusted container-level forwarding policy. Multiple replicas do not share quotas; internet-facing deployments should retain edge rate limiting. The client quota can also be abused to delay a legitimate login, so a `rate_limited` increase requires investigation rather than automatic permanent blocking.

### SSRF residual risk

DNS can change between policy validation and the HTTP client's connection. The application validates on registration and immediately before delivery, but a hardened internet-facing deployment should additionally enforce egress policy at the network layer or use a dedicated outbound proxy that validates the connected IP.

`APP_SECURITY_ALLOW_PRIVATE_TARGETS=true` exists for the Docker demo receiver. Production deployments keep it `false`.

### Receiver verification

Consumers should:

1. Read the raw body without reformatting it.
2. Reject timestamps outside a short tolerance window.
3. Calculate `HMAC-SHA256(secret, timestamp + "." + rawBody)`.
4. Compare the expected and supplied signatures in constant time.
5. Deduplicate by `X-Webhook-Id`.

## 中文

### 保护对象

- Endpoint 签名密钥
- 事件数据
- 投递历史和错误上下文
- 内部网络访问能力
- Worker 可用性
- 操作者会话与管理 API 访问权限

### 安全控制

| 威胁 | 控制措施 |
| --- | --- |
| 持久化密钥泄露 | 使用部署时提供的主密钥进行 AES-256-GCM 加密 |
| API 返回密钥 | Endpoint 响应不包含签名密钥 |
| 轮换时泄露密钥 | 只写密钥输入、加密替换，以及仅含元数据的提交后审计事件 |
| SSRF 访问内部基础设施 | 仅支持 HTTP(S)、DNS 地址检查、私有地址策略和端口白名单 |
| 利用重定向绕过策略 | 禁止自动跟随重定向 |
| 注册后 DNS 发生变化 | 每次投递前重新执行 URL 策略 |
| 不安全的 Endpoint 配置修改 | 每次目标 URL 编辑都在持久化前重新执行与注册时相同的完整 URL 策略 |
| 资源耗尽 | 请求超时、响应大小限制、反向代理 Payload 限制、出站并发上限和有限重试 |
| 伪造投递 | 对时间戳与完整请求体执行 HMAC-SHA256 |
| 接收方时序攻击 | Demo Receiver 使用常量时间比较 |
| Worker 异常退出 | 数据库租约过期后可以重新抢占 |
| 重复处理 | 稳定事件 ID 与明确的 at-least-once 契约 |
| 未授权访问控制台或 API | 部署提供操作者凭据、使用 BCrypt 校验，并建立服务端 HTTP-only 会话 |
| 登录会话固定攻击 | 认证成功后轮换 Session ID |
| 跨站请求伪造 | 所有不安全方法的 API 请求必须携带会话绑定的 CSRF Token |
| 在线密码猜测 | 在 BCrypt 校验前执行按远端地址和单个进程计算的双层配额 |
| 凭据或 Token 泄露到日志 | 认证日志只记录结果元数据 |

`APP_SECURITY_MASTER_KEY` 是 Base64 编码的 32 字节密钥。更换密钥时需要执行受控迁移，先用旧密钥解密，再用新密钥加密已有 Endpoint 数据。

Endpoint 签名密钥轮换必须携带运维端最后观察到的版本。陈旧请求返回 HTTP `409`；轮换成功后 Endpoint 版本递增，API 与日志均不返回新旧密钥。已接收任务保留各自的加密密钥快照，因此轮换前任务进入终态前，接收端必须同时接受新旧密钥。

Endpoint 目标 URL 编辑沿用相同的观察版本，并在持久化前重新执行完整 URL 安全策略。成功后的审计事件只包含变更标记，不包含新旧 URL；已接收任务保留原目标快照，因此编辑操作无法重定向排队或重试中的任务。

当前版本是带原生会话认证的单操作者工程控制台。通过部署密钥管理设置 `APP_OPERATOR_USERNAME` 和至少 16 字符的唯一 `APP_OPERATOR_PASSWORD`；应用使用 BCrypt 校验密码，且不持久化密码。

会话 Cookie 使用 HTTP-only 与 `SameSite=Lax`；在 HTTPS 入口后应设置 `APP_OPERATOR_COOKIE_SECURE=true`。不安全方法的 API 请求还必须携带 `GET /api/auth/csrf` 返回、并保存在服务端 Session 中的 CSRF Token。登录成功会轮换 Session ID 与 CSRF Token，登出会使服务端安全上下文失效并清理 Session Cookie。

原生认证不能替代网络边界：控制台和 API 应位于 HTTPS 后，管理端点仍应限制在私有运维网络。多人账号、角色、团队、租户隔离、密码找回和外部身份提供方均不在 v1.0 范围内。

### 登录滥用防护

每个进程在一分钟窗口内允许每个远端地址尝试 8 次、全局尝试 64 次。超过客户端配额后阻止该地址 5 分钟，超过全局配额后阻止该进程 1 分钟。拒绝发生在 BCrypt 校验之前，返回 HTTP `429 login_rate_limited` 与 `Retry-After`。进程最多保留 1,024 个客户端条目；登录成功只清除当前客户端状态，不重置全局配额。

策略由 `APP_OPERATOR_LOGIN_CLIENT_MAX_ATTEMPTS`、`APP_OPERATOR_LOGIN_GLOBAL_MAX_ATTEMPTS`、`APP_OPERATOR_LOGIN_WINDOW`、`APP_OPERATOR_LOGIN_CLIENT_BLOCK_DURATION`、`APP_OPERATOR_LOGIN_GLOBAL_BLOCK_DURATION` 与 `APP_OPERATOR_LOGIN_MAX_CLIENT_ENTRIES` 控制。指标只暴露带 `success`、`failure`、`rate_limited`、`logout` 四种 `outcome` 的 `webhook_operator_authentication_total`；可用 `sum by (outcome) (rate(webhook_operator_authentication_total[5m]))` 排查变化。

限流状态位于单个进程内，直接使用 Servlet 看到的远端地址，应用代码不信任转发头。位于反向代理后时，请求可能共享代理地址，除非部署在容器层配置可信转发策略；多个副本也不共享配额，因此公网入口仍应保留边缘限流。攻击者还可能利用客户端配额延迟合法登录，`rate_limited` 上升应触发人工排查，而不是自动永久封禁。

Docker 演示环境使用 `APP_SECURITY_ALLOW_PRIVATE_TARGETS=true` 访问内部 Receiver；生产环境保持为 `false`，并建议通过网络出口策略或专用 Outbound Proxy 进一步控制实际连接地址。
