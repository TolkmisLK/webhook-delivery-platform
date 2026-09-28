import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as pause } from 'node:timers/promises';
import { DemoClient } from './demo-client.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function batchSize(value = '3') {
  assert.match(value, /^[1-5]$/, 'DRILL_BATCH_SIZE must be an integer from 1 to 5');
  return Number(value);
}

export function attemptSummary(detail, { phase, terminalRequired = false } = {}) {
  const { delivery, attempts } = detail;
  assert.equal(delivery.attemptCount, attempts.length, `${phase}: attempt count mismatch`);
  attempts.forEach((attempt, index) => assert.equal(attempt.attemptNumber, index + 1, `${phase}: non-contiguous attempts`));
  if (terminalRequired) assert.equal(delivery.status, 'SUCCEEDED', `${phase}: delivery did not succeed`);
  if (terminalRequired) assert.equal(delivery.lastStatusCode, 204, `${phase}: final status code mismatch`);
  if (phase === 'baseline') {
    assert.equal(attempts.length, 1, 'baseline must finish in one attempt');
    assert.equal(attempts[0].statusCode, 204, 'baseline receiver must return 204');
  }
  if (phase === 'backlog') {
    assert.ok(attempts.length >= 1, 'backlog needs a real failed attempt');
    assert.equal(delivery.status, 'RETRY_SCHEDULED', 'backlog must remain retryable');
    assert.ok(attempts.every(attempt => attempt.outcome === 'RETRY_SCHEDULED' && attempt.statusCode !== 204));
  }
  if (phase === 'recovered') {
    assert.ok(attempts.length >= 2, 'recovered delivery needs failure and success attempts');
    assert.equal(attempts.at(-1).statusCode, 204, 'recovered receiver must return 204');
    assert.ok(attempts.slice(0, -1).some(attempt => attempt.outcome === 'RETRY_SCHEDULED' && attempt.statusCode !== 204));
  }
  return {
    deliveryId: delivery.id,
    status: delivery.status,
    attemptCount: delivery.attemptCount,
    attempts: attempts.map(({ attemptNumber, statusCode, outcome, durationMs }) => ({ attemptNumber, statusCode, outcome, durationMs })),
  };
}

function compose(project, ...args) {
  try {
    execFileSync('docker', ['compose', '-p', project, ...args], { cwd: root, encoding: 'utf8', timeout: 60000, stdio: 'pipe' });
  } catch {
    throw new Error(`Compose operation failed: ${args.join(' ')}`);
  }
}

async function waitFor(check, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await check();
    if (result) return result;
    await pause(500);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function waitForReadiness(origin) {
  await waitFor(async () => {
    try {
      const response = await fetch(new URL('/actuator/health/readiness', origin), { signal: AbortSignal.timeout(2000), redirect: 'error' });
      return response.ok;
    } catch { return false; }
  }, 90000, 'backend readiness after restart');
}

async function waitForReceiver(project) {
  await waitFor(async () => {
    try { compose(project, 'exec', '-T', 'receiver', 'wget', '-qO-', 'http://127.0.0.1:8090/health'); return true; }
    catch { return false; }
  }, 30000, 'receiver readiness after restoration');
}

async function publishBatch(client, endpointId, runId, phase, count) {
  const ids = [];
  for (let index = 0; index < count; index++) {
    const event = await client.request('/api/events', {
      endpointId,
      eventType: `drill.${phase}`,
      idempotencyKey: `${runId}-${phase}-${index}`,
      data: { phase, index },
    });
    assert.equal(event.duplicate, false, 'drill event unexpectedly duplicated');
    ids.push(event.deliveryId);
  }
  return ids;
}

async function waitForDeliveries(client, ids, phase, timeoutMs) {
  return waitFor(async () => {
    const details = await Promise.all(ids.map(id => client.request(`/api/deliveries/${id}`)));
    for (const detail of details) {
      if (detail.delivery.status === 'DEAD' || detail.delivery.status === 'CANCELED') {
        throw new Error(`${phase}: delivery reached ${detail.delivery.status}`);
      }
      if (phase === 'backlog' && detail.delivery.status === 'SUCCEEDED') {
        throw new Error('Outage delivery succeeded before backlog evidence was recorded');
      }
    }
    if (phase === 'backlog') {
      if (!details.every(detail => detail.delivery.status === 'RETRY_SCHEDULED' && detail.attempts.length >= 1)) return null;
    } else if (!details.every(detail => detail.delivery.status === 'SUCCEEDED')) return null;
    return details.map(detail => attemptSummary(detail, { phase, terminalRequired: phase !== 'backlog' }));
  }, timeoutMs, `${phase} delivery states`);
}

export async function runDrill(env = process.env) {
  const project = env.COMPOSE_PROJECT_NAME;
  assert.match(project ?? '', /^webhook-drill-[1-9]\d*-[1-9]\d*$/, 'Use a unique CI Compose project name');
  const count = batchSize(env.DRILL_BATCH_SIZE);
  assert.equal(env.APP_DELIVERY_WORKER_CONCURRENCY, '2', 'Drill worker concurrency must be 2');
  assert.equal(env.APP_DELIVERY_BATCH_SIZE, '2', 'Drill claim batch size must be 2');
  assert.equal(env.APP_DELIVERY_MAX_ATTEMPTS, '5', 'Drill maximum attempts must be 5');
  const client = new DemoClient({ baseUrl: env.DEMO_BASE_URL ?? 'http://127.0.0.1:8088' });
  assert.ok(['localhost', '127.0.0.1'].includes(client.origin.hostname), 'Drill API must stay on loopback');

  const started = Date.now();
  const runId = randomUUID();
  const report = {
    schemaVersion: 1,
    result: 'RUNNING',
    runId,
    sourceSha: env.GITHUB_SHA ?? null,
    startedAt: new Date(started).toISOString(),
    conditions: { baselineEvents: count, outageEvents: count, maximumEvents: 10, workerConcurrency: 2, claimBatchSize: 2, maxAttempts: 5 },
    observations: {},
    timings: {},
  };
  let activeClient = client;
  try {
    await client.login({ username: env.APP_OPERATOR_USERNAME ?? 'admin', password: env.APP_OPERATOR_PASSWORD ?? 'local-admin-password' });
    const endpoint = await client.request('/api/endpoints', {
      name: `Controlled drill ${runId}`,
      url: 'http://receiver:8090/hooks',
      secret: env.DEMO_WEBHOOK_SECRET ?? 'local-demo-secret',
    });
    const baselineStarted = Date.now();
    const baselineIds = await publishBatch(client, endpoint.id, runId, 'baseline', count);
    report.observations.baseline = await waitForDeliveries(client, baselineIds, 'baseline', 45000);
    report.timings.baselineElapsedMs = Date.now() - baselineStarted;
    report.timings.baselineObservedCompletionsPerSecond = Number((count * 1000 / Math.max(1, report.timings.baselineElapsedMs)).toFixed(3));

    compose(project, 'stop', 'receiver');
    const outageStarted = Date.now();
    const outageIds = await publishBatch(client, endpoint.id, runId, 'outage', count);
    report.observations.backlog = await waitForDeliveries(client, outageIds, 'backlog', 30000);
    report.timings.outageToBacklogMs = Date.now() - outageStarted;

    const recoveryStarted = Date.now();
    compose(project, 'stop', 'backend');
    compose(project, 'start', 'receiver');
    await waitForReceiver(project);
    compose(project, 'start', 'backend');
    await waitForReadiness(client.origin);
    let oldSessionRejected = false;
    try { await client.request('/api/auth/session'); }
    catch (error) { oldSessionRejected = error.status === 401; }
    assert.equal(oldSessionRejected, true, 'Old operator session survived backend restart');
    report.observations.oldSessionRejected = oldSessionRejected;

    activeClient = new DemoClient({ baseUrl: client.origin.href });
    await activeClient.login({ username: env.APP_OPERATOR_USERNAME ?? 'admin', password: env.APP_OPERATOR_PASSWORD ?? 'local-admin-password' });
    report.observations.recovered = await waitForDeliveries(activeClient, outageIds, 'recovered', 120000);
    report.timings.recoveryElapsedMs = Date.now() - recoveryStarted;
    report.timings.outageToFinalMs = Date.now() - outageStarted;
    report.result = 'PASS';
  } catch (error) {
    report.result = 'FAIL';
    report.failure = String(error.message).slice(0, 240);
    throw error;
  } finally {
    report.finishedAt = new Date().toISOString();
    report.elapsedMs = Date.now() - started;
    await writeFile(path.join(root, 'drill-summary.json'), JSON.stringify(report, null, 2) + '\n');
    process.stdout.write(JSON.stringify(report, null, 2) + '\n');
    await activeClient.logout().catch(() => {});
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runDrill().catch(() => { process.exitCode = 1; });
}
