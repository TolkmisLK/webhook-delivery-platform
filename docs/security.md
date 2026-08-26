# Security model / 安全模型

[English](#english) · [中文](#中文)

## English

### Protected assets

- Endpoint signing secrets
- Event payloads
- Delivery history and error context
- Internal network reachability
- Worker availability

### Controls

| Threat | Control |
| --- | --- |
| Secret disclosure at rest | AES-256-GCM with a deployment-provided master key |
| Secret disclosure through API | Endpoint responses never contain the signing secret |
| Secret disclosure during rotation | Write-only secret input, encrypted replacement, metadata-only after-commit audit event |
| SSRF to local infrastructure | HTTP(S)-only URLs, DNS resolution checks, private-address policy, and port allowlist |
| Redirect-based policy bypass | Redirect following is disabled |
| DNS changes after registration | URL policy is evaluated again immediately before delivery |
| Resource exhaustion | Request timeout, response-size bound, payload limit at the reverse proxy, bounded outbound concurrency and retry count |
| Forged delivery | HMAC-SHA256 over timestamp and exact body |
| Timing attack in receiver | Included receiver uses constant-time signature comparison |
| Worker crash | Expired database leases are reclaimable |
| Duplicate processing | Stable event ID and documented at-least-once contract |

### Master key

`APP_SECURITY_MASTER_KEY` is a Base64-encoded 32-byte key. Rotate it through a controlled migration that decrypts each endpoint with the old key and encrypts it with the new key. Do not replace it without migrating existing ciphertext.

### Endpoint secret rotation

Endpoint signing-secret rotation requires the version last observed by the operator. A stale request returns HTTP `409`; successful rotation increments the Endpoint version and never returns or logs either secret. Accepted delivery jobs keep their encrypted-secret snapshots, so receivers must accept both old and new secrets until pre-rotation deliveries reach terminal states.

### Deployment boundary

The current release is a single-operator engineering console. Deploy it behind an authenticated reverse proxy or a private network boundary. Native account and authorization policy are planned before any shared deployment.

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

### 安全控制

| 威胁 | 控制措施 |
| --- | --- |
| 持久化密钥泄露 | 使用部署时提供的主密钥进行 AES-256-GCM 加密 |
| API 返回密钥 | Endpoint 响应不包含签名密钥 |
| 轮换时泄露密钥 | 只写密钥输入、加密替换，以及仅含元数据的提交后审计事件 |
| SSRF 访问内部基础设施 | 仅支持 HTTP(S)、DNS 地址检查、私有地址策略和端口白名单 |
| 利用重定向绕过策略 | 禁止自动跟随重定向 |
| 注册后 DNS 发生变化 | 每次投递前重新执行 URL 策略 |
| 资源耗尽 | 请求超时、响应大小限制、反向代理 Payload 限制、出站并发上限和有限重试 |
| 伪造投递 | 对时间戳与完整请求体执行 HMAC-SHA256 |
| 接收方时序攻击 | Demo Receiver 使用常量时间比较 |
| Worker 异常退出 | 数据库租约过期后可以重新抢占 |
| 重复处理 | 稳定事件 ID 与明确的 at-least-once 契约 |

`APP_SECURITY_MASTER_KEY` 是 Base64 编码的 32 字节密钥。更换密钥时需要执行受控迁移，先用旧密钥解密，再用新密钥加密已有 Endpoint 数据。

Endpoint 签名密钥轮换必须携带运维端最后观察到的版本。陈旧请求返回 HTTP `409`；轮换成功后 Endpoint 版本递增，API 与日志均不返回新旧密钥。已接收任务保留各自的加密密钥快照，因此轮换前任务进入终态前，接收端必须同时接受新旧密钥。

当前版本是单操作者工程控制台。部署时应放在带认证的反向代理或私有网络边界之后；在支持多人共享部署前，将增加系统自身的账号与授权策略。

Docker 演示环境使用 `APP_SECURITY_ALLOW_PRIVATE_TARGETS=true` 访问内部 Receiver；生产环境保持为 `false`，并建议通过网络出口策略或专用 Outbound Proxy 进一步控制实际连接地址。
