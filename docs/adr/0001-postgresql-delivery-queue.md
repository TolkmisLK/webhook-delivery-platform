# ADR-0001: PostgreSQL-backed delivery queue / 基于 PostgreSQL 的投递队列

- Status / 状态: Accepted / 已接受
- Date / 日期: 2026-08-24

## Context / 背景

The first release needs durable acceptance, retry scheduling, abandoned-job recovery, and a local one-command demo. Adding a separate broker would create a second consistency boundary before traffic or throughput justifies it.

首个版本需要持久化接收、重试调度、异常任务恢复和本地一键演示。在吞吐量需求尚未证明前，引入独立消息队列会额外增加一致性边界。

## Decision / 决策

Store immutable events and delivery jobs in PostgreSQL. Workers claim batches with `FOR UPDATE SKIP LOCKED`, commit the lease, perform network I/O, and persist an attempt outcome in a new transaction.

使用 PostgreSQL 保存不可变事件和投递任务。Worker 通过 `FOR UPDATE SKIP LOCKED` 抢占任务，提交租约后执行网络请求，再在新事务中保存尝试结果。

## Consequences / 影响

Positive / 优点：

- Event acceptance and job creation share one transaction.
  事件接收与任务创建处于同一个事务；在返回成功前，两者一起持久化。
- A single backup contains operational state.
  一份数据库备份包含队列运行状态；恢复时仍需配套的主密钥和部署配置。
- Multiple workers can claim jobs without a central coordinator.
  多个 Worker 可在无需中心协调器的情况下抢占任务。
- The complete system runs with one infrastructure dependency.
  系统只需要 PostgreSQL 这一项基础设施依赖。

Trade-offs / 代价：

- Polling adds bounded latency and database load.
  轮询带来额外等待时间及数据库负载。
- At-least-once delivery requires idempotent consumers.
  至少一次投递要求接收方按事件 ID 去重，处理重复副作用。
- Very high throughput may eventually justify a broker or log-based transport.
  如果吞吐量显著提高，将来可能需要消息代理或基于日志的传输层。

Revisit the decision only after benchmarks show PostgreSQL contention or polling cost outside the project target.

只有基准测试证明 PostgreSQL 争用或轮询成本超出项目目标时，才重新评估这一决策。
