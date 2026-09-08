# 看一次失败后的自动重试 / Retry walkthrough

这个演示会向接收端发送一个事件。接收端先返回两次 HTTP 503，第三次返回 HTTP 204。平台负责等待和重试，接收端会校验每次请求的签名。

The receiver returns HTTP 503 twice, then HTTP 204. The platform schedules the retries and the receiver checks every request's signature.

## 启动 / Start

需要 Docker Compose。下载或克隆仓库，在仓库目录运行：

```bash
cp .env.example .env
docker compose up --build --wait
```

Windows PowerShell 可以用 `Copy-Item .env.example .env` 复制文件。如果已有 `.env`，沿用自己的配置即可。

Open http://localhost:8088 after Compose finishes starting. The local defaults are `admin` / `local-admin-password`.

## 在网页里试 / Use the console

1. 打开 http://localhost:8088，使用 `admin` / `local-admin-password` 登录；改过 `.env` 就用自己的值。
2. 新建接收端，名称填“重试演示”，地址填 `http://receiver:8090/hooks/flaky?failures=2`，签名密钥填 `local-demo-secret`。如果修改过 `DEMO_WEBHOOK_SECRET`，这里也要一致。
3. 选择刚创建的接收端，事件类型填 `demo.retry`，幂等键填写一个未用过的值，如 `retry-demo-001`，内容填 `{"message":"Hello"}`，然后发布。
4. 在投递列表找到新任务，打开“查看详情 / Inspect”。前两次请求会失败，等待下一次重试后，任务变为 `SUCCEEDED`。

Create an endpoint with the URL and signing secret above, publish a `demo.retry` event with a fresh idempotency key, then select **Inspect** on the new delivery.

| 次数 / Attempt | HTTP | 结果 / Outcome |
| --- | --- | --- |
| 1 | 503 | `RETRY_SCHEDULED`，等待重试 |
| 2 | 503 | `RETRY_SCHEDULED`，再次等待 |
| 3 | 204 | `SUCCEEDED`，已送达 |

默认重试间隔依次为 5 秒和 10 秒，实际还受任务轮询和请求耗时影响。打开详情较晚时，前面的步骤可能已经完成，仍可以查看历史记录。

The default retry delays are 5 and 10 seconds, plus polling and request time. The history remains available if you open the detail view after the delivery finishes.

## 用命令运行同一个演示 / Run the scripted example

本机安装 Node.js 22 或更新版本后，在已启动 Compose 的仓库目录运行：

```bash
node --env-file=.env scripts/demo-retry.mjs
```

脚本会登录本地控制台 API，创建一个接收端和一个事件，显示各次结果，并核对最终状态。它只连接本地演示地址；不会连接外部接收服务。每次运行会留下新的演示记录，方便回到控制台查看。

With Node.js 22+, this command creates an endpoint and event in the running local stack, prints the attempts, and checks the final result. Each run leaves its demo records in the console.

输出应包含 / Expected output:

```text
Attempt 1: HTTP 503 → RETRY_SCHEDULED
Attempt 2: HTTP 503 → RETRY_SCHEDULED
Attempt 3: HTTP 204 → SUCCEEDED
PASS: 503 → 503 → 204; final status SUCCEEDED
```

仓库的 [Retry demo 工作流](https://github.com/TolkmisLK/webhook-delivery-platform/actions/workflows/demo.yml) 会运行同一个命令。打开成功的运行记录，查看 **Run retry example**，或下载 `retry-demo-output` 获取文本结果。

The **Retry demo** workflow runs the same example. Its **Run retry example** step and `retry-demo-output` artifact contain the output.

## 卡住时检查什么 / Troubleshooting

| 现象 / Symptom | 检查 / Check |
| --- | --- |
| 无法打开控制台 / Connection refused | 运行 `docker compose ps`，确认服务已经启动，8088 端口没有被其他程序占用。 |
| 登录返回 401 / Login fails | 确认 `.env` 中的账号密码和已启动的 Compose 一致；修改后重新执行 `docker compose up -d`。 |
| 接收端返回 401 / Receiver rejects signature | 接收端的签名密钥需要和 `DEMO_WEBHOOK_SECRET` 一致。 |
| 发布后没有新任务 / No new delivery | 换一个幂等键；同一接收端重复使用相同幂等键会返回原事件。 |
| 不知道为什么失败 / Delivery keeps failing | 打开任务详情，运行 `docker compose logs receiver` 查看接收端记录。 |

`receiver` 是 Compose 内部的服务名。在自己的浏览器访问控制台用 `localhost:8088`，在接收端配置里使用 `receiver:8090`。

`receiver` resolves inside Compose. Use `localhost:8088` for the console and `receiver:8090` for the endpoint URL.

演示结束后运行 `docker compose down`，会停止服务并保留数据库数据。再次运行 `docker compose up -d` 可继续查看记录。

Run `docker compose down` to stop the demo while keeping its database. Start it again with `docker compose up -d`.
