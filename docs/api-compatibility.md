# HTTP API compatibility / HTTP API 兼容性

## English

### Status and scope

This policy applies from v1.0.0 to the documented `/api/**` HTTP surface, the named delivery server-sent events, and the outbound webhook signing contract. The OpenAPI document is the machine-readable source of truth for routes, methods, parameters, schemas, and application error responses.

The React console, internal Java types, database schema, log wording, Prometheus scrape paths, and Spring Boot management endpoints are not public API unless another document explicitly says otherwise.

### Stable 1.x surface

The following are compatibility commitments throughout the 1.x release line:

- documented route paths, HTTP methods, success status codes, and request content types;
- required request fields and their validation bounds;
- response property names, JSON types, and documented nullability;
- `operationId` values and application error `code` values;
- the `JSESSIONID` cookie authentication boundary and CSRF header workflow;
- the `connected` and `delivery` SSE event names and their documented JSON data;
- delivery-status values already published at v1.0.0;
- HMAC-SHA256 signing headers and the `<timestamp>.<exact request body>` canonical input;
- endpoint-scoped idempotency and at-least-once delivery semantics.

### Compatible changes in 1.x

A minor or patch release may make the following backward-compatible changes:

- add a new route or operation;
- add an optional request field;
- add an optional response field that clients can ignore;
- add a new optional response header;
- add a more specific documented error response without changing an existing success response;
- tighten implementation security without narrowing a documented valid input;
- clarify descriptions, examples, and operational guidance.

Clients must ignore unknown response properties. A new enum value is compatibility-significant: it is announced in the changelog and a minor release, and clients should retain an unknown-value fallback. Existing enum values are not removed or renamed in 1.x.

### Breaking changes

The following require a new major version unless they correct a security vulnerability that cannot be mitigated compatibly:

- remove or rename a route, method, field, error code, operation ID, header, SSE event, or existing enum value;
- change a field's JSON type or documented nullability;
- make an optional request field required or narrow an accepted validation range;
- change success status semantics, idempotency behavior, delivery guarantees, or signature canonicalization;
- expose secret material that was previously excluded.

Urgent security corrections are documented with impact, migration guidance, and the narrowest safe compatibility exception.

### Deprecation

Deprecated operations or fields are marked in OpenAPI, the changelog, and bilingual documentation. They remain available for at least the next minor release and are removed only in a new major release, except for the urgent security exception above.

### Authentication, errors, and request correlation

Every protected operation explicitly documents `401 unauthenticated`. Every unsafe operation protected by CSRF explicitly documents `403 access_denied`. Authentication failures, validation failures, conflicts, and missing resources use the stable `ApiError` envelope.

Clients may send `X-Request-Id` using 1–100 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, `:`, and `-`. A missing or unsafe value is replaced with a generated UUID. Every response returns the effective value in `X-Request-Id`; JSON error responses repeat the same value in `requestId`. Request IDs are correlation metadata, not authentication or idempotency tokens.

### Release verification

Each release must keep backend, frontend, and OpenAPI versions aligned; pass Java, PostgreSQL integration, frontend, OpenAPI, formatting, and dependency-audit gates; and record user-visible contract changes in the changelog.

## 中文

### 状态与范围

本政策从 v1.0.0 起适用于已记录的 `/api/**` HTTP 边界、具名投递 SSE 事件，以及出站 Webhook 签名契约。OpenAPI 文档是路由、方法、参数、Schema 与应用错误响应的机器可读事实来源。

除非其他文档明确说明，React 控制台、内部 Java 类型、数据库 Schema、日志措辞、Prometheus 抓取路径和 Spring Boot 管理端点都不属于公共 API。

### 1.x 稳定边界

整个 1.x 版本线承诺兼容以下内容：

- 已记录的路由路径、HTTP 方法、成功状态码与请求内容类型；
- 必填请求字段及其校验边界；
- 响应属性名、JSON 类型与已记录的可空性；
- `operationId` 与应用错误 `code`；
- `JSESSIONID` Cookie 认证边界与 CSRF Header 流程；
- `connected`、`delivery` SSE 事件名及其已记录 JSON 数据；
- v1.0.0 已发布的投递状态值；
- HMAC-SHA256 签名 Header 与 `<timestamp>.<原始请求体>` 规范输入；
- Endpoint 级幂等与至少一次投递语义。

### 1.x 内的兼容变更

次版本或补丁版本可以进行以下向后兼容变更：

- 增加新路由或操作；
- 增加可选请求字段；
- 增加客户端可忽略的可选响应字段；
- 增加可选响应 Header；
- 在不改变现有成功响应的前提下增加更具体的错误响应；
- 在不缩小已记录合法输入范围的前提下强化实现安全；
- 澄清说明、示例与运维指导。

客户端必须忽略未知响应属性。新增枚举值属于重要兼容性变化：必须在 Changelog 与次版本中公布，客户端也应保留未知值回退。1.x 内不会移除或重命名已有枚举值。

### 破坏性变更

除非为了修复无法兼容缓解的安全漏洞，以下变更必须进入新的主版本：

- 移除或重命名路由、方法、字段、错误码、Operation ID、Header、SSE 事件或已有枚举值；
- 修改字段 JSON 类型或已记录可空性；
- 将可选请求字段改为必填，或缩小已接受的校验范围；
- 修改成功状态语义、幂等行为、投递保证或签名规范化方式；
- 暴露此前明确排除的密钥材料。

紧急安全修复必须记录影响、迁移指导，并采用范围最小的安全兼容例外。

### 弃用

弃用的操作或字段必须同时标记在 OpenAPI、Changelog 与双语文档中；除上述紧急安全例外外，至少保留到下一个次版本，并且只在新的主版本中移除。

### 认证、错误与请求关联

每个受保护操作都明确记录 `401 unauthenticated`；每个受 CSRF 保护的不安全操作都明确记录 `403 access_denied`。认证失败、校验失败、冲突与资源不存在均使用稳定的 `ApiError` 包装。

客户端可以发送由 `A-Z`、`a-z`、`0-9`、`.`、`_`、`:`、`-` 组成的 1–100 字符 `X-Request-Id`。缺失或不安全的值会被替换为生成的 UUID。每个响应都通过 `X-Request-Id` 返回最终值；JSON 错误响应还会在 `requestId` 中重复相同值。Request ID 只用于关联，不是认证凭据或幂等 Token。

### 发布验证

每个版本都必须保持后端、前端与 OpenAPI 版本一致，通过 Java、PostgreSQL 集成、前端、OpenAPI、格式与依赖审计门禁，并在 Changelog 中记录用户可见契约变化。
