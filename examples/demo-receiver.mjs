import { createHmac, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";

const port = Number(process.env.PORT ?? 8090);
const secret = process.env.WEBHOOK_SIGNING_SECRET ?? "local-demo-secret";

const server = createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    response.writeHead(200).end("ok");
    return;
  }

  if (request.method !== "POST" || request.url !== "/hooks") {
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

  console.log(JSON.stringify({
    eventId: request.headers["x-webhook-id"],
    eventType: request.headers["x-webhook-type"],
    verified: true,
  }));
  response.writeHead(204).end();
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Demo receiver listening on :${port}`);
});

function safeEqual(left, right) {
  if (typeof left !== "string" || left.length !== right.length) return false;
  return timingSafeEqual(Buffer.from(left), Buffer.from(right));
}
