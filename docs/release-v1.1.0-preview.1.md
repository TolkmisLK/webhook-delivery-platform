# v1.1.0-preview.1 · source preview / 源码预览

This prerelease contains source code and a Docker Compose walkthrough. It is a preview, not a stable production release. No hosted service, credentials, or binary package is included.

此预览版提供源码和 Docker Compose 本地演示，不代表稳定生产版本；不包含托管服务、凭据或二进制安装包。

## Changes / 变更

- The operations console can filter delivery history by status and receiving Endpoint, move between pages, and distinguish loading from empty results. Slower earlier requests cannot replace the current page or reopen an inspection after the view changes.
- `GET /api/deliveries/page` applies filters in PostgreSQL, orders by creation time and ID, accepts pages 0–10000 and sizes 1–100, and returns `items`, `page`, `size`, and `hasNext`. The existing `GET /api/deliveries?limit=...` array response remains available.
- Four history indexes support the unfiltered, status, Endpoint, and combined queries. The migration is `V3__delivery_history_indexes.sql`.

- 控制台可按投递状态和接收地址筛选投递历史、翻页，并明确区分加载中与无结果。较慢的旧请求不能覆盖当前页面，也不能在切换视图后重新打开旧详情。
- `GET /api/deliveries/page` 在 PostgreSQL 中筛选，按创建时间与 ID 排序；页码范围 0–10000、每页 1–100 条，返回 `items`、`page`、`size`、`hasNext`。原 `GET /api/deliveries?limit=...` 数组接口仍可用。
- 新增四个历史查询索引，分别覆盖无筛选、状态、接收地址和组合筛选；迁移文件为 `V3__delivery_history_indexes.sql`。

Numbered pages use an offset. New jobs or status changes between requests may move records between pages. The page limit bounds a single query; it is not a stable snapshot or a full export. Delivery retry, signing, and execution behavior are unchanged.

页码使用 offset；两次请求之间新增任务或状态变化可能使记录移动。页码上限约束单次查询，不提供固定快照或全量导出。投递重试、签名及执行行为未改变。

## Verification and limits / 验证与边界

- The publication job runs only after the tag's Java/PostgreSQL integration tests, frontend checks, browser tests, OpenAPI lint, dependency audit, and isolated Compose signed-retry smoke test succeed. It also requires the tag commit to equal the current `main` commit and verifies the version fields before publishing.
- The Compose smoke test exercises the included local receiver and retry script. It does not validate an external receiver, a production deployment, throughput, or long-running reliability.
- For any upgrade beyond the local walkthrough, follow the [production checklist](https://github.com/TolkmisLK/webhook-delivery-platform/blob/v1.1.0-preview.1/docs/production-readiness.md) and [recovery runbook](https://github.com/TolkmisLK/webhook-delivery-platform/blob/v1.1.0-preview.1/docs/operations-recovery.md). The V3 index migration runs during startup and needs an appropriate maintenance plan for a large existing database.

- 发布任务只有在标签对应的 Java/PostgreSQL 集成测试、前端检查、浏览器测试、OpenAPI 校验、依赖审计和隔离 Compose 签名重试冒烟测试全部成功后运行；同时核对标签提交与当前 `main` 提交完全相同，并检查版本字段。
- Compose 冒烟测试使用仓库内本地接收端与重试脚本；它不能证明外部接收端、生产部署、吞吐能力或长期可靠性。
- 非本地演示的升级请遵循[生产检查表](https://github.com/TolkmisLK/webhook-delivery-platform/blob/v1.1.0-preview.1/docs/production-readiness.md)和[恢复手册](https://github.com/TolkmisLK/webhook-delivery-platform/blob/v1.1.0-preview.1/docs/operations-recovery.md)。V3 索引迁移在启动时执行；已有大数据库需要相应维护安排。
