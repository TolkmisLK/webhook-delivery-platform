# 受控小批量与故障恢复演练

本演练只在独立 GitHub Actions 运行器上启动仓库的开发 Compose 栈，验证少量投递在接收端短时不可用、后端正常停止并启动后能否依数据库记录继续完成。它不使用生产数据，不测试公网吞吐量，也不等于容量或 SLA 验收。

## 运行与隔离

打开 [Controlled load and recovery drill 工作流](https://github.com/TolkmisLK/webhook-delivery-platform/actions/workflows/controlled-drill.yml)，选择仓库分支运行。`batch_size` 默认为 3，每阶段仅允许 1–5 条；两阶段总共最多 10 条。工作流将 worker 并发数和数据库领取批次都固定为 2，单条任务最多尝试 5 次。脚本对这些条件再次校验，超限时在发布事件前退出。

每次 CI 使用由运行编号和重试次数生成的独立 Compose project。控制台只绑定运行器的 `127.0.0.1:8088`，接收端只在 Compose 内网；工作流结束仅对该 project 执行 `docker compose down --volumes`。使用仓库 `.env.example` 的公开开发值，不接入外部端点。不要把这个开发栈或已知账号密码暴露到公网。

演练顺序：

1. 登录本地控制台，创建一个内部接收端，发布第一批事件。逐条通过投递详情确认 `SUCCEEDED`，且真实 attempt 只有一次、HTTP 204。
2. 停止接收端，发布第二批事件。逐条等到数据库记录至少一次失败 attempt、状态为 `RETRY_SCHEDULED`，以此确认出现可重试积压；不把仅有 `PENDING` 当作已尝试失败。
3. 正常停止后端，恢复接收端，再启动后端。确认旧会话被拒绝（401）并重新登录；逐条等待最终 `SUCCEEDED`，核对 attempt 编号连续、数量与任务计数一致、失败后有 HTTP 204 成功记录。

工作流上传 `controlled-drill-summary` Artifact，内含 `drill-summary.json`。摘要来自实际 API 响应，列出每条任务的终态、attempt 号、结果、HTTP 状态码与持续时间。时间数据分开记录：基线批次从发布首条事件到全部成功的耗时及该小样本的完成速率，故障批次从停止接收端到观察到积压的耗时，恢复阶段从正常停止后端到全部成功的耗时，以及整个故障至完成的耗时；这些时间包含轮询与重试等待，不能当成纯投递吞吐量。不输出 Cookie、CSRF、密码、签名密钥、响应正文或原始错误信息。失败时保留已取得的阶段证据和简短失败原因，作业以失败结束。GitHub Artifact 有保留期，需通过具体运行记录核对提交 SHA、作业结论与摘要；不能把脚本中的预期条件写成已完成结果。

## 首次真实 CI 结果（2026-09-28）

[演练运行 #36400452423](https://github.com/TolkmisLK/webhook-delivery-platform/actions/runs/36400452423) 成功，`drill` 作业中的演练、摘要上传和独立项目清理步骤均成功。[完整 JSON Artifact](https://github.com/TolkmisLK/webhook-delivery-platform/actions/runs/36400452423/artifacts/10959764038) 对应 PR 分支提交 `ab61413c70b8e7859d6c93617b89c58daba491df`；作业检出的 PR 测试合并提交为 `6d5b8f817cc10ab7f76360862843950c4fef1e80`，因此 JSON 的 `sourceSha` 是后者。该记录来自 GitHub 托管运行器，**未在本机 Docker 或实体设备执行**。同一提交的 [Retry demo](https://github.com/TolkmisLK/webhook-delivery-platform/actions/runs/36400452369) 和 [常规 CI](https://github.com/TolkmisLK/webhook-delivery-platform/actions/runs/36400452379) 也成功。

条件：基线 3 条、故障 3 条，worker 并发 2、领取批次 2、每条最多尝试 5 次；仅使用 CI 内部接收端和 loopback 控制台。六条任务逐条查询了实际 API 详情：

| 阶段 | 任务 ID 前缀 | 观察到的 attempt 和最终状态 |
| --- | --- | --- |
| 基线 | `090f0c94`、`fc2de32f`、`9dd0bf9b` | 各 1 次 HTTP 204，均 `SUCCEEDED`。 |
| 接收端停止后 | `2b36781a`、`d5415a7f`、`a5830db3` | 各先出现 2 次 `RETRY_SCHEDULED`，HTTP 状态码均为 `null`；积压快照时三条仍为 `RETRY_SCHEDULED`。 |
| 接收端恢复、后端正常重启后 | 同上三条 | 各第 3 次 HTTP 204，最终均 `SUCCEEDED`，`attemptCount=3`。旧操作者会话返回 401，随后重新登录成功。 |

这三条失败 attempt 的记录没有 HTTP 响应码，单次持续约 5 秒；摘要未暴露原始异常，不能仅凭 `null` 判定底层错误类别。基线从首条发布到全部成功耗时 **1,816 ms**，本次三条小样本完成速率 **1.652 条/秒**。停止接收端到观察到全部积压为 **22,033 ms**；从正常停止后端开始到全部恢复成功为 **16,166 ms**；整个故障至完成为 **38,199 ms**。后三者包含重试退避、请求超时、容器操作、重新登录和轮询，不能与基线完成速率直接比较。运行总耗时（含登录、建端点等）为 **50,685 ms**；这些是一次 CI 观测值，不是稳定性能指标。

接收端故障是容器停止造成的连接失败，不涵盖网络分区、数据库崩溃、接收端已处理但未响应、生产密钥恢复或大规模压力。后端正常停止并启动会丢失进程内登录会话；数据库与加密主密钥仍保持相同。有关备份和正式恢复边界，见 [升级、备份与恢复](operations-recovery.md)。
