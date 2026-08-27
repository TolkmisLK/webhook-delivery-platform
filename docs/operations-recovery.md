# Upgrade, backup, and recovery / 升级、备份与恢复

[English](#english) · [中文](#中文)

## English

### Purpose and recovery objectives

This runbook covers the repository's Docker Compose deployment. Adapt the database-administration commands to the equivalent managed PostgreSQL controls when necessary, while preserving the same data and secret boundaries.

Choose and record deployment-specific objectives before production use:

- **RPO:** the time of the last verified PostgreSQL dump. An online dump is transactionally consistent, but changes committed after its snapshot are not recoverable from it.
- **RTO:** restore time includes archive retrieval, database recreation, image checkout, secret/configuration recovery, Flyway validation, and smoke tests. Measure it with regular restore drills instead of assuming a value.
- Run a restore drill before v1.0 production adoption, after a database or deployment change, and at least quarterly. Store the measured RPO/RTO and the drill owner outside the repository.

At-least-once delivery still applies after recovery. A receiver may have accepted a request after the database snapshot but before the incident; restoring that snapshot can send the event again. Receivers must deduplicate by `X-Webhook-Id`.

### Recovery inventory

| Asset | Recovery class | Required action |
| --- | --- | --- |
| PostgreSQL tables and `flyway_schema_history` | Authoritative, durable | Back up with `pg_dump` in custom format and restore as one consistent archive. |
| Endpoint and delivery-job signing-secret ciphertext | Inside PostgreSQL | Restore with the database; it is unusable without the exact matching master key. |
| `APP_SECURITY_MASTER_KEY` | External critical secret | Back up its secret-manager version separately. Never replace it during restore or upgrade. |
| Database credentials and operator username/password | External secrets | Recover from the deployment secret store. They are not stored by the application dump. |
| Non-secret application settings, Compose file, image/source revision | External configuration | Version and retain them with the release manifest. |
| HTTP sessions and login-throttle counters | Ephemeral process state | Not backed up. Operators sign in again; rate-limit windows restart. |
| Prometheus data | Optional operational history | Not authoritative queue state. Back it up separately only if monitoring retention requires it. |
| Demo receiver state | Development-only | Do not include it in a production recovery set. |

The database archive contains event bodies, URLs, delivery errors, response excerpts, and encrypted secrets. Treat it as sensitive even though signing secrets are ciphertext. Encrypt backups at rest and in transit, restrict restore access, audit retrieval, and never copy production data into a lower-trust environment.

The database-level dump does not include cluster-global roles or role passwords; those remain external recovery items. Restore only an archive with trusted provenance because PostgreSQL restore executes the SQL objects stored in that archive.

Do not save `docker compose config` output: variable interpolation can place secret values in the generated file. Record only image/source identity, non-secret configuration change IDs, and secret-manager **version identifiers**, not secret values. Keep the master-key recovery material separate from the database archive so one storage compromise does not provide both.

### Backup procedure

Run from the deployed repository checkout. The examples assume the Compose database and user names (`webhooks`). Set a protected path outside the repository and use a new directory for every backup:

```bash
umask 077
export RECOVERY_ROOT=/secure-backups/webhook-delivery-platform
export RECOVERY_SET="$RECOVERY_ROOT/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RECOVERY_SET"
test -d "$RECOVERY_SET"
```

Confirm PostgreSQL health, create an online transactionally consistent archive, and validate that PostgreSQL can read its table of contents:

```bash
docker compose exec -T postgres pg_isready -U webhooks -d webhooks
docker compose exec -T postgres pg_dump \
  -U webhooks -d webhooks \
  --format=custom --compress=9 --no-owner --no-privileges \
  > "$RECOVERY_SET/webhooks.dump"
test -s "$RECOVERY_SET/webhooks.dump"
docker compose exec -T postgres pg_restore --list \
  < "$RECOVERY_SET/webhooks.dump" \
  > "$RECOVERY_SET/webhooks.toc"
test -s "$RECOVERY_SET/webhooks.toc"
```

For a maintenance-window snapshot that prevents application writes during the dump, stop public ingress and the backend first. If the dump fails, restart them before investigating:

```bash
docker compose stop frontend backend
docker compose exec -T postgres pg_dump \
  -U webhooks -d webhooks \
  --format=custom --compress=9 --no-owner --no-privileges \
  > "$RECOVERY_SET/webhooks.dump"
docker compose start backend frontend
```

Stopping workers cannot retract a webhook already accepted by a receiver. Non-terminal jobs in the snapshot may run after recovery and can produce duplicates.

Record release identity without rendering interpolated configuration, then generate checksums:

```bash
git rev-parse HEAD > "$RECOVERY_SET/source-revision.txt"
git status --porcelain > "$RECOVERY_SET/source-status.txt"
docker compose version > "$RECOVERY_SET/compose-version.txt"
docker compose images > "$RECOVERY_SET/compose-images.txt"
(
  cd "$RECOVERY_SET"
  sha256sum webhooks.dump webhooks.toc source-revision.txt \
    source-status.txt compose-version.txt compose-images.txt > SHA256SUMS
)
```

A production recovery set is complete only when its external inventory also records:

1. the master-key secret version that encrypted the dump's data;
2. database and operator credential secret versions;
3. the non-secret configuration revision and deployment/reverse-proxy revision;
4. the source tag/SHA or immutable image digests;
5. backup timestamp, retention class, operator, environment, and expected RPO.

Store that inventory in the protected backup catalog, not in this repository. Upload the archive and checksum only after `sha256sum -c SHA256SUMS` succeeds. Retain at least one recovery set from before every upgrade until the upgrade and restore drill are accepted. Apply the organization's longer legal and incident-response retention requirements when applicable.

### Forward upgrade

1. Read the target release notes and migration files. Confirm the target release is newer than the current `flyway_schema_history` and that the current release supports the documented upgrade path.
2. Create and verify a recovery set. For a strict rollback point, use the maintenance-window backup.
3. Confirm the current master-key, database-credential, operator-credential, and non-secret configuration version identifiers. Do not rotate the master key as part of an ordinary application upgrade.
4. Stop ingress and the backend, then deploy the target immutable tag/SHA or image digest.
5. Start PostgreSQL and the target backend. Flyway validates applied checksums and applies forward migrations before the application becomes ready.
6. Inspect startup logs for Flyway checksum, migration, key-decoding, or schema-validation errors. Do not mark the upgrade successful while readiness is down.
7. Start the frontend, complete the smoke tests below, watch queue age/error metrics, and retain the pre-upgrade recovery set through the acceptance window.

Compose sequence:

```bash
docker compose stop frontend backend
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U webhooks -d webhooks
docker compose up -d --build backend
docker compose logs --no-color backend
docker compose up -d --build frontend
```

Review migration history after the backend is ready:

```bash
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select installed_rank, version, description, script, checksum, success from flyway_schema_history order by installed_rank;"
```

### Restore procedure

Restore drills must use an isolated database and blocked outbound network. A restored production queue contains real target URLs and may immediately resume delivery when the backend starts. Do not start the backend in a drill until egress is blocked. For production disaster recovery, open egress only after the restored queue and receiver ownership are approved.

Preflight:

1. Identify the exact recovery set, source/image revision, master-key version, credential versions, configuration revision, and intended restore environment.
2. Verify the recovery set before stopping anything:

   ```bash
   export RECOVERY_SET=/secure-backups/webhook-delivery-platform/20260827T000000Z
   test -f "$RECOVERY_SET/webhooks.dump"
   (cd "$RECOVERY_SET" && sha256sum -c SHA256SUMS)
   docker compose exec -T postgres pg_restore --list \
     < "$RECOVERY_SET/webhooks.dump" > /dev/null
   ```

3. Confirm that the target release can read the archive's Flyway version. Recover the exact matching `APP_SECURITY_MASTER_KEY`; a different valid 32-byte key can allow startup but cannot decrypt existing ciphertext.
4. Take a separate backup of the current target database if it might be needed. The next step destroys the target database.

During an approved maintenance window, stop application access, recreate a clean database, and restore with error-stop behavior:

```bash
docker compose stop frontend backend
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U webhooks -d postgres
docker compose exec -T postgres psql -U webhooks -d postgres \
  -v ON_ERROR_STOP=1 -c \
  "select pg_terminate_backend(pid) from pg_stat_activity where datname = 'webhooks' and pid <> pg_backend_pid();"
docker compose exec -T postgres dropdb -U webhooks \
  --maintenance-db=postgres --if-exists webhooks
docker compose exec -T postgres createdb -U webhooks \
  --maintenance-db=postgres --owner=webhooks webhooks
docker compose exec -T postgres pg_restore \
  -U webhooks -d webhooks --exit-on-error --no-owner --no-privileges \
  < "$RECOVERY_SET/webhooks.dump"
```

The Compose `POSTGRES_USER` owns the demo cluster and can recreate the database. A managed service may require its administrator role instead; do not grant broader application runtime privileges solely to copy these commands.

Before starting the backend, inspect migration history and row counts:

```bash
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select installed_rank, version, description, checksum, success from flyway_schema_history order by installed_rank;"
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select 'webhook_endpoint' as table_name, count(*) from webhook_endpoint union all select 'webhook_event', count(*) from webhook_event union all select 'delivery_job', count(*) from delivery_job union all select 'delivery_attempt', count(*) from delivery_attempt;"
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select status, count(*) from delivery_job group by status order by status;"
```

Configure the recorded release, exact master key, credentials, and non-secret settings. In a drill, keep outbound traffic blocked. Start the backend and require Flyway/schema validation and readiness before starting the frontend:

```bash
docker compose up -d --build backend
docker compose logs --no-color backend
docker compose up -d --build frontend
curl -fsS http://localhost:8088/actuator/health/readiness
```

### Post-restore smoke tests

Use `curl`, `jq`, and a temporary cookie jar to verify the public access boundary. Supply credentials from the restored deployment secret store; do not write them into the recovery set:

```bash
export BASE_URL=http://localhost:8088
export APP_OPERATOR_USERNAME=admin
read -rsp "Operator password: " OPERATOR_PASSWORD
echo
COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT
CSRF_JSON=$(curl -fsS -c "$COOKIE_JAR" "$BASE_URL/api/auth/csrf")
CSRF_HEADER=$(printf '%s' "$CSRF_JSON" | jq -r .headerName)
CSRF_TOKEN=$(printf '%s' "$CSRF_JSON" | jq -r .token)
LOGIN_BODY=$(jq -nc --arg username "$APP_OPERATOR_USERNAME" \
  --arg password "$OPERATOR_PASSWORD" '{username:$username,password:$password}')
curl -fsS -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H 'Content-Type: application/json' -H "$CSRF_HEADER: $CSRF_TOKEN" \
  -d "$LOGIN_BODY" "$BASE_URL/api/auth/login" | jq -e .username
curl -fsS -b "$COOKIE_JAR" "$BASE_URL/api/endpoints" | jq -e 'type == "array"'
timeout 10s curl -fsSN -b "$COOKIE_JAR" \
  "$BASE_URL/api/deliveries/stream" | grep -m1 -E '^event: *connected\r?$'
unset OPERATOR_PASSWORD LOGIN_BODY CSRF_JSON CSRF_HEADER CSRF_TOKEN
```

Then complete a controlled delivery test:

1. Keep all other outbound destinations blocked.
2. Approve one recovery canary receiver whose signing secret and ownership are known.
3. Replay one restored terminal canary delivery, or publish a new uniquely identified canary event to a restored Endpoint.
4. Require `SUCCEEDED`, a new committed attempt, the expected `X-Webhook-Id`, and successful receiver-side HMAC verification.
5. A new Endpoint tests encryption with the current key; only a restored Endpoint/delivery tests that the recovered master key decrypts existing ciphertext.
6. Review `webhook_delivery_jobs`, oldest-runnable age, authentication outcomes, backend errors, and unexpected receiver traffic before opening normal egress.

### Rollback and failure handling

Flyway migrations are forward-only. A code-only rollback is allowed only when the previous release explicitly supports every schema version already applied. Never start an older binary against a newer schema by assumption.

The strict rollback path is:

1. Stop ingress and backend workers.
2. Select the verified pre-upgrade recovery set.
3. Restore its database into a clean database using the restore procedure above.
4. Restore the matching previous release, master-key version, credentials, and configuration revision.
5. Complete all smoke tests before reopening egress and ingress.

Stop and investigate rather than continuing when any of these occurs:

- checksum or `pg_restore --list` failure: the archive is incomplete or corrupted;
- Flyway checksum/history mismatch: release and database provenance disagree;
- master-key mismatch: restored ciphertext cannot be decrypted; recover the exact key version rather than overwriting data;
- non-empty `source-status.txt`: the backed-up deployment included uncommitted source changes that must be explained;
- active workers or open egress during a drill: real receivers may receive duplicates;
- restore into a lower-trust environment: payloads, URLs, error excerpts, and ciphertext may be exposed;
- missing external secret/configuration inventory: the database alone is not a complete recovery set.

The incident commander owns the restore decision and RPO selection; the database operator owns archive verification and database recreation; the deployment operator owns release/configuration identity and egress controls; the security owner controls master-key and credential recovery; the application owner approves queue state and the canary delivery before traffic is reopened. Record names and evidence in the incident or drill log.

## 中文

### 目的与恢复目标

本手册适用于仓库中的 Docker Compose 部署。使用托管 PostgreSQL 时，可将数据库管理命令替换为对应平台操作，但必须保持相同的数据与密钥边界。

生产使用前必须选择并记录部署自己的目标：

- **RPO：** 最近一次已验证 PostgreSQL Dump 的时间。在线 Dump 具有事务一致性，但其快照之后提交的变更无法从该备份恢复。
- **RTO：** 包括获取归档、重建数据库、检出镜像/源码、恢复密钥与配置、Flyway 校验和冒烟测试。必须通过恢复演练测量，而不能假定。
- v1.0 投产前、数据库或部署方式变更后，以及至少每季度执行一次恢复演练。将实测 RPO/RTO 与负责人记录在仓库之外。

恢复后仍保持至少一次投递语义。Receiver 可能在数据库快照之后、故障之前已接收请求；恢复该快照后事件可能再次发送，因此 Receiver 必须按 `X-Webhook-Id` 去重。

### 恢复清单

| 资产 | 恢复分类 | 必须执行的操作 |
| --- | --- | --- |
| PostgreSQL 表与 `flyway_schema_history` | 权威、持久化 | 使用自定义格式 `pg_dump` 备份，并作为一个一致归档恢复。 |
| Endpoint 与投递任务签名密钥密文 | 位于 PostgreSQL | 随数据库恢复；没有完全匹配的主密钥就无法使用。 |
| `APP_SECURITY_MASTER_KEY` | 外部关键密钥 | 单独备份 Secret Manager 版本；恢复或升级时不得替换。 |
| 数据库凭据、操作者用户名/密码 | 外部密钥 | 从部署密钥存储恢复；应用数据库 Dump 不包含这些内容。 |
| 非密配置、Compose 文件、镜像/源码版本 | 外部配置 | 随发布清单进行版本管理和保留。 |
| HTTP Session 与登录限流计数 | 进程临时状态 | 不备份；操作者重新登录，限流窗口重新开始。 |
| Prometheus 数据 | 可选运维历史 | 不是权威队列状态；只有监控保留策略需要时才单独备份。 |
| Demo Receiver 状态 | 仅开发环境 | 不得纳入生产恢复集。 |

数据库归档包含事件正文、URL、投递错误、响应摘要和加密密钥。即使签名密钥为密文，也必须将归档视为敏感数据：静态与传输过程都要加密，限制并审计恢复访问，禁止将生产数据复制到低信任环境。

数据库级 Dump 不包含集群全局 Role 或 Role 密码；它们仍属于外部恢复项。PostgreSQL Restore 会执行归档中保存的 SQL 对象，因此只能恢复来源可信的归档。

不要保存 `docker compose config` 输出，因为变量插值可能把密钥值写入文件。只记录镜像/源码身份、非密配置变更 ID，以及 Secret Manager 的**版本 ID**而不是密钥值。主密钥恢复材料必须与数据库归档分开存储，避免单个存储位置泄露后同时获得二者。

### 备份流程

在已部署的仓库 Checkout 中运行。以下命令采用 Compose 默认数据库与用户 `webhooks`。先在仓库之外设置受保护路径，并为每次备份创建新目录：

```bash
umask 077
export RECOVERY_ROOT=/secure-backups/webhook-delivery-platform
export RECOVERY_SET="$RECOVERY_ROOT/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RECOVERY_SET"
test -d "$RECOVERY_SET"
```

确认 PostgreSQL 健康，创建在线事务一致归档，并验证 PostgreSQL 能读取其目录：

```bash
docker compose exec -T postgres pg_isready -U webhooks -d webhooks
docker compose exec -T postgres pg_dump \
  -U webhooks -d webhooks \
  --format=custom --compress=9 --no-owner --no-privileges \
  > "$RECOVERY_SET/webhooks.dump"
test -s "$RECOVERY_SET/webhooks.dump"
docker compose exec -T postgres pg_restore --list \
  < "$RECOVERY_SET/webhooks.dump" \
  > "$RECOVERY_SET/webhooks.toc"
test -s "$RECOVERY_SET/webhooks.toc"
```

如果需要在 Dump 期间禁止应用写入的维护窗口快照，先停止公网入口与 Backend；Dump 失败时应先恢复服务，再排查：

```bash
docker compose stop frontend backend
docker compose exec -T postgres pg_dump \
  -U webhooks -d webhooks \
  --format=custom --compress=9 --no-owner --no-privileges \
  > "$RECOVERY_SET/webhooks.dump"
docker compose start backend frontend
```

停止 Worker 无法撤回 Receiver 已接收的 Webhook；快照内的非终态任务恢复后可能重新执行并产生重复投递。

记录发布身份时不要渲染插值配置，然后生成校验和：

```bash
git rev-parse HEAD > "$RECOVERY_SET/source-revision.txt"
git status --porcelain > "$RECOVERY_SET/source-status.txt"
docker compose version > "$RECOVERY_SET/compose-version.txt"
docker compose images > "$RECOVERY_SET/compose-images.txt"
(
  cd "$RECOVERY_SET"
  sha256sum webhooks.dump webhooks.toc source-revision.txt \
    source-status.txt compose-version.txt compose-images.txt > SHA256SUMS
)
```

完整生产恢复集还必须在外部清单中记录：

1. 加密 Dump 内数据的主密钥版本；
2. 数据库与操作者凭据版本；
3. 非密配置版本与部署/反向代理版本；
4. 源码 Tag/SHA 或不可变镜像 Digest；
5. 备份时间、保留类别、操作者、环境与预期 RPO。

清单应保存在受保护的备份目录系统中，而不是本仓库。只有 `sha256sum -c SHA256SUMS` 成功后才能上传归档和校验和。每次升级前的恢复集至少保留到升级与恢复演练验收完成；如组织有更长的合规或事件响应保留要求，以更长者为准。

### 前向升级

1. 阅读目标 Release Note 与迁移文件；确认目标版本高于当前 `flyway_schema_history`，并且当前版本支持该升级路径。
2. 创建并验证恢复集；需要严格回滚点时使用维护窗口备份。
3. 确认当前主密钥、数据库凭据、操作者凭据和非密配置的版本 ID。普通应用升级不得同时轮换主密钥。
4. 停止入口与 Backend，再部署不可变目标 Tag/SHA 或镜像 Digest。
5. 启动 PostgreSQL 与目标 Backend。Flyway 会在应用 Ready 前校验已应用迁移的 Checksum 并执行前向迁移。
6. 检查启动日志中的 Flyway Checksum、迁移、密钥解码或 Schema 校验错误；Readiness 未恢复时不得宣布升级成功。
7. 启动 Frontend，完成下文冒烟测试，观察队列年龄与错误指标，并在验收窗口结束前保留升级前恢复集。

Compose 操作顺序：

```bash
docker compose stop frontend backend
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U webhooks -d webhooks
docker compose up -d --build backend
docker compose logs --no-color backend
docker compose up -d --build frontend
```

Backend Ready 后检查迁移历史：

```bash
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select installed_rank, version, description, script, checksum, success from flyway_schema_history order by installed_rank;"
```

### 恢复流程

恢复演练必须使用隔离数据库并阻断出站网络。恢复的生产队列包含真实目标 URL，Backend 启动后可能立即继续投递。演练中在阻断 Egress 前不得启动 Backend；生产灾难恢复也必须在审批恢复队列与 Receiver 所有权之后才能开放 Egress。

预检：

1. 确认恢复集、源码/镜像版本、主密钥版本、凭据版本、配置版本与目标恢复环境。
2. 停止任何服务前先验证恢复集：

   ```bash
   export RECOVERY_SET=/secure-backups/webhook-delivery-platform/20260827T000000Z
   test -f "$RECOVERY_SET/webhooks.dump"
   (cd "$RECOVERY_SET" && sha256sum -c SHA256SUMS)
   docker compose exec -T postgres pg_restore --list \
     < "$RECOVERY_SET/webhooks.dump" > /dev/null
   ```

3. 确认目标 Release 能读取归档中的 Flyway 版本；恢复完全匹配的 `APP_SECURITY_MASTER_KEY`。不同但同为合法 32 字节的密钥可能允许应用启动，却无法解密已有密文。
4. 如果当前目标数据库可能需要保留，先为其创建独立备份；下一步将销毁目标数据库。

在已批准的维护窗口内停止应用访问，重建干净数据库，并使用遇错即停方式恢复：

```bash
docker compose stop frontend backend
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U webhooks -d postgres
docker compose exec -T postgres psql -U webhooks -d postgres \
  -v ON_ERROR_STOP=1 -c \
  "select pg_terminate_backend(pid) from pg_stat_activity where datname = 'webhooks' and pid <> pg_backend_pid();"
docker compose exec -T postgres dropdb -U webhooks \
  --maintenance-db=postgres --if-exists webhooks
docker compose exec -T postgres createdb -U webhooks \
  --maintenance-db=postgres --owner=webhooks webhooks
docker compose exec -T postgres pg_restore \
  -U webhooks -d webhooks --exit-on-error --no-owner --no-privileges \
  < "$RECOVERY_SET/webhooks.dump"
```

Compose 的 `POSTGRES_USER` 拥有 Demo 数据库集群，因此可以重建数据库；托管服务可能需要使用管理员角色。不要为了照抄命令而扩大应用运行时权限。

启动 Backend 前检查迁移历史与行数：

```bash
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select installed_rank, version, description, checksum, success from flyway_schema_history order by installed_rank;"
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select 'webhook_endpoint' as table_name, count(*) from webhook_endpoint union all select 'webhook_event', count(*) from webhook_event union all select 'delivery_job', count(*) from delivery_job union all select 'delivery_attempt', count(*) from delivery_attempt;"
docker compose exec -T postgres psql -U webhooks -d webhooks \
  -v ON_ERROR_STOP=1 -c \
  "select status, count(*) from delivery_job group by status order by status;"
```

配置清单记录的 Release、完全匹配的主密钥、凭据和非密设置。演练中继续阻断出站流量；启动 Backend 并要求 Flyway/Schema 校验和 Readiness 成功后，再启动 Frontend：

```bash
docker compose up -d --build backend
docker compose logs --no-color backend
docker compose up -d --build frontend
curl -fsS http://localhost:8088/actuator/health/readiness
```

### 恢复后冒烟测试

使用 `curl`、`jq` 和临时 Cookie Jar 验证公开访问边界。凭据必须来自已恢复的部署密钥存储，不得写入恢复集：

```bash
export BASE_URL=http://localhost:8088
export APP_OPERATOR_USERNAME=admin
read -rsp "Operator password: " OPERATOR_PASSWORD
echo
COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT
CSRF_JSON=$(curl -fsS -c "$COOKIE_JAR" "$BASE_URL/api/auth/csrf")
CSRF_HEADER=$(printf '%s' "$CSRF_JSON" | jq -r .headerName)
CSRF_TOKEN=$(printf '%s' "$CSRF_JSON" | jq -r .token)
LOGIN_BODY=$(jq -nc --arg username "$APP_OPERATOR_USERNAME" \
  --arg password "$OPERATOR_PASSWORD" '{username:$username,password:$password}')
curl -fsS -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H 'Content-Type: application/json' -H "$CSRF_HEADER: $CSRF_TOKEN" \
  -d "$LOGIN_BODY" "$BASE_URL/api/auth/login" | jq -e .username
curl -fsS -b "$COOKIE_JAR" "$BASE_URL/api/endpoints" | jq -e 'type == "array"'
timeout 10s curl -fsSN -b "$COOKIE_JAR" \
  "$BASE_URL/api/deliveries/stream" | grep -m1 -E '^event: *connected\r?$'
unset OPERATOR_PASSWORD LOGIN_BODY CSRF_JSON CSRF_HEADER CSRF_TOKEN
```

随后完成受控投递测试：

1. 继续阻断所有其他出站目标。
2. 仅批准一个已知签名密钥与负责人的恢复 Canary Receiver。
3. 重放一个已恢复的终态 Canary 投递，或向已恢复 Endpoint 发布带唯一 ID 的新 Canary 事件。
4. 必须观察到 `SUCCEEDED`、新的已提交 Attempt、预期 `X-Webhook-Id`，以及 Receiver 侧 HMAC 校验成功。
5. 新 Endpoint 只能验证当前密钥对新数据的加解密；只有已恢复 Endpoint/投递才能证明恢复的主密钥能解密已有密文。
6. 开放正常 Egress 前，检查 `webhook_delivery_jobs`、最老可执行任务年龄、认证结果、Backend 错误和意外 Receiver 流量。

### 回滚与失败处理

Flyway 迁移只前进。只有旧版本明确支持全部已应用 Schema 时，才能只回滚代码；禁止凭假设让旧二进制运行在新 Schema 上。

严格回滚路径：

1. 停止入口与 Backend Worker。
2. 选择已验证的升级前恢复集。
3. 按恢复流程将其数据库恢复到干净数据库。
4. 恢复匹配的旧 Release、主密钥版本、凭据与配置版本。
5. 所有冒烟测试通过后才能重新开放 Egress 与 Ingress。

出现以下任一情况时必须停止并排查，而不能继续：

- Checksum 或 `pg_restore --list` 失败：归档不完整或损坏；
- Flyway Checksum/历史不一致：Release 与数据库来源不匹配；
- 主密钥不匹配：恢复密文无法解密；必须找回完全匹配的密钥版本，不能覆盖数据；
- `source-status.txt` 非空：备份部署包含未提交源码变更，必须解释；
- 演练时 Worker 活跃或 Egress 开放：真实 Receiver 可能收到重复投递；
- 恢复到低信任环境：Payload、URL、错误摘要与密文可能泄露；
- 缺少外部密钥/配置清单：仅有数据库并不构成完整恢复集。

事件指挥负责恢复决策与 RPO 选择；数据库运维负责归档验证和数据库重建；部署运维负责 Release/配置身份与 Egress 控制；安全负责人控制主密钥与凭据恢复；应用负责人在重新开放流量前审批队列状态与 Canary 投递。所有人员与证据都应记录在事件或演练日志中。
