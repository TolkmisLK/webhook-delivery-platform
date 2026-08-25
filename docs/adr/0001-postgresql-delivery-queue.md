# ADR-0001: PostgreSQL-backed delivery queue

- Status / 状态: Accepted / 已接受
- Date / 日期: 2026-08-24

## Context / 背景

The first release needs durable acceptance, retry scheduling, abandoned-job recovery, and a local one-command demo. Adding a separate broker would create a second consistency boundary before traffic or throughput justifies it.

首个版本需要持久化接收、重试调度、异常任务恢复和本地一键演示。在吞吐量需求尚未证明前，引入独立消息队列会额外增加一致性边界。

## Decision / 决策

Store immutable events and delivery jobs in PostgreSQL. Workers claim batches with `FOR UPDATE SKIP LOCKED`, commit the lease, perform network I/O, and persist an attempt outcome in a new transaction.

使用 PostgreSQL 保存不可变事件和投递任务。Worker 通过 `FOR UPDATE SKIP LOCKED` 抢占任务，提交租约后执行网络请求，再在新事务中保存尝试结果。

## Consequences / 影响

Positive:

- Event acceptance and job creation share one transaction.
- A single backup contains operational state.
- Multiple workers can claim jobs without a central coordinator.
- The complete system runs with one infrastructure dependency.

Trade-offs:

- Polling adds bounded latency and database load.
- At-least-once delivery requires idempotent consumers.
- Very high throughput may eventually justify a broker or log-based transport.

Revisit the decision only after benchmarks show PostgreSQL contention or polling cost outside the project target.

优点是事务边界清晰、运维依赖少，并能支持多个 Worker。代价是轮询延迟、数据库负载和 at-least-once 语义。只有性能数据证明出现瓶颈时，才重新评估独立消息队列。
