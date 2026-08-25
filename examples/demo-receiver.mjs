import { createHmac, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";

const port = Number(process.env.PORT ?? 8090);
const secret = process.env.WEBHOOK_SIGNING_SECRET ?? "local-demo-secret";
const attemptsByEvent = new Map();

const server = createServer(async (request, response) => {
  const requestUrl = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);

  if (request.method === "GET" && requestUrl.pathname === "/health") {
    response.writeHead(200).end("ok");
    return;
  }

  const isStandardHook = requestUrl.pathname === "/hooks";
  const isFlakyHook = requestUrl.pathname === "/hooks/flaky";
  if (request.method !== "POST" || (!isStandardHook && !isFlakyHook)) {
    response.writeHead(404).end("not found");
    return;
  }

  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 1024 * 1024) {
      response.writeHead(413).end("payload too large");
      return;
    }
    chunks.push(chunk);
  }

  const body = Buffer.concat(chunks).toString("utf8");
  const timestamp = request.headers["x-webhook-timestamp"];
  const supplied = request.headers["x-webhook-signature"];
  const expected = timestamp
    ? `v1=${createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex")}`
    : "";

  if (!safeEqual(supplied, expected)) {
    response.writeHead(401).end("invalid signature");
    return;
  }

  const eventId = request.headers["x-webhook-id"];
  const eventType = request.headers["x-webhook-type"];
  if (typeof eventId !== "string") {
    response.writeHead(400).end("missing event id");
    return;
  }

  let failureBudget = 0;
  if (isFlakyHook) {
    failureBudget = parseFailureBudget(requestUrl.searchParams.get("failures"));
    if (failureBudget === null) {
      response.writeHead(400).end("failures must be an integer from 0 to 10");
      return;
    }
  }

  const attempt = (attemptsByEvent.get(eventId) ?? 0) + 1;
  attemptsByEvent.set(eventId, attempt);
  const statusCode = attempt <= failureBudget ? 503 : 204;

  console.log(JSON.stringify({
    eventId,
    eventType,
    attempt,
    statusCode,
    verified: true,
  }));
  if (statusCode === 503) {
    response.writeHead(503, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ error: "controlled transient failure", attempt }));
    return;
  }

  attemptsByEvent.delete(eventId);
  response.writeHead(204).end();
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Demo receiver listening on :${port}`);
});

function safeEqual(left, right) {
  if (typeof left !== "string" || left.length !== right.length) return false;
  return timingSafeEqual(Buffer.from(left), Buffer.from(right));
}

function parseFailureBudget(value) {
  if (value === null) return 2;
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed <= 10 ? parsed : null;
}
