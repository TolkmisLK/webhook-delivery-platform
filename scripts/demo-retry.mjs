import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';

// This script creates one endpoint and one event in the local Compose demo.
const origin = new URL(process.env.DEMO_BASE_URL ?? 'http://localhost:8088');
assert.ok(
  origin.protocol === 'http:' && ['localhost', '127.0.0.1', 'frontend'].includes(origin.hostname),
  'DEMO_BASE_URL must point to the local Compose demo',
);
assert.ok(!origin.username && !origin.password && origin.pathname === '/' && !origin.search && !origin.hash,
  'Use an origin without credentials, a path, query, or fragment');
const cookies = new Map();
let csrf;
async function request(path, body) {
  const response = await fetch(new URL(path, origin), {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: [...cookies].map(([key, value]) => `${key}=${value}`).join('; '),
      ...(body !== undefined && csrf ? { [csrf.headerName]: csrf.token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'error',
    signal: AbortSignal.timeout(10000),
  });
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';')[0];
    const separator = pair.indexOf('=');
    cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
  }
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}. Check the demo settings and docs/demo.md.`);
  return response.status === 204 ? undefined : response.json();
}

async function main() {
  csrf = await request('/api/auth/csrf');
  await request('/api/auth/login', {
    username: process.env.APP_OPERATOR_USERNAME ?? 'admin',
    password: process.env.APP_OPERATOR_PASSWORD ?? 'local-admin-password',
  });
  csrf = await request('/api/auth/csrf');
  process.stdout.write('Signed in to the local demo / 已登录本地演示\n');
  const endpoint = await request('/api/endpoints', {
    name: `Retry demo ${new Date().toISOString()}`,
    url: 'http://receiver:8090/hooks/flaky?failures=2',
    secret: process.env.DEMO_WEBHOOK_SECRET ?? 'local-demo-secret',
  });
  const event = await request('/api/events', {
    endpointId: endpoint.id,
    eventType: 'demo.retry',
    idempotencyKey: `demo-${randomUUID()}`,
    data: { message: 'Retry demonstration' },
  });
  process.stdout.write(`Delivery / 投递任务: ${event.deliveryId}\n`);
  const deadline = Date.now() + 90000;
  const seen = new Set();
  while (Date.now() < deadline) {
    const detail = await request(`/api/deliveries/${event.deliveryId}`);
    const attempts = [...detail.attempts].sort((a, b) => a.attemptNumber - b.attemptNumber);
    for (const attempt of attempts) {
      if (seen.has(attempt.attemptNumber)) continue;
      seen.add(attempt.attemptNumber);
      process.stdout.write(`Attempt ${attempt.attemptNumber}: HTTP ${attempt.statusCode} → ${attempt.outcome}\n`);
    }
    if (detail.delivery.status === 'SUCCEEDED') {
      assert.deepEqual(attempts.map((attempt) => attempt.statusCode), [503, 503, 204]);
      assert.equal(detail.delivery.attemptCount, 3);
      process.stdout.write('PASS: 503 → 503 → 204; final status SUCCEEDED\n');
      process.stdout.write('Open the console and inspect this delivery / 可在控制台查看这条任务的详情\n');
      return;
    }
    if (['DEAD', 'CANCELED'].includes(detail.delivery.status)) {
      throw new Error(`Delivery ended with ${detail.delivery.status}`);
    }
    await pause(1000);
  }
  throw new Error('Timed out after 90 seconds; inspect the delivery and receiver logs');
}

try {
  await main();
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
} finally {
  if (csrf) await request('/api/auth/logout', {}).catch(() => {});
}
