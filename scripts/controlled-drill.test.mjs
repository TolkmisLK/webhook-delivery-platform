import test from 'node:test';
import assert from 'node:assert/strict';
import { DemoClient } from './demo-client.mjs';
import { attemptSummary, batchSize } from './controlled-drill.mjs';

const attempt = (attemptNumber, outcome, statusCode) => ({ attemptNumber, outcome, statusCode, durationMs: 12, errorMessage: 'must not appear' });
const detail = (status, attempts) => ({ delivery: { id: 'delivery-1', status, attemptCount: attempts.length, lastStatusCode: attempts.at(-1)?.statusCode }, attempts });

test('small-batch limit prevents unbounded event submission', () => {
  assert.equal(batchSize(), 3);
  assert.equal(batchSize('5'), 5);
  for (const value of ['0', '6', '100', '2.5', 'abc']) assert.throws(() => batchSize(value), /1 to 5/);
});

test('summary accepts only observed, contiguous attempts and excludes error bodies', () => {
  const baseline = attemptSummary(detail('SUCCEEDED', [attempt(1, 'SUCCEEDED', 204)]), { phase: 'baseline', terminalRequired: true });
  assert.equal(baseline.attemptCount, 1);
  assert.equal(JSON.stringify(baseline).includes('must not appear'), false);
  const backlog = attemptSummary(detail('RETRY_SCHEDULED', [attempt(1, 'RETRY_SCHEDULED', null)]), { phase: 'backlog' });
  assert.equal(backlog.status, 'RETRY_SCHEDULED');
  const recovered = attemptSummary(detail('SUCCEEDED', [attempt(1, 'RETRY_SCHEDULED', null), attempt(2, 'SUCCEEDED', 204)]), { phase: 'recovered', terminalRequired: true });
  assert.equal(recovered.attemptCount, 2);
  assert.throws(() => attemptSummary({ delivery: { id: 'x', status: 'SUCCEEDED', attemptCount: 3 }, attempts: [attempt(1, 'SUCCEEDED', 204)] }, { phase: 'baseline' }), /count mismatch/);
  assert.throws(() => attemptSummary(detail('SUCCEEDED', [attempt(2, 'SUCCEEDED', 204)]), { phase: 'baseline' }), /non-contiguous/);
  assert.throws(() => attemptSummary(detail('RETRY_SCHEDULED', [attempt(1, 'RETRY_SCHEDULED', null)]), { phase: 'recovered', terminalRequired: true }), /did not succeed/);
});

test('shared demo client carries CSRF and session cookies through login and logout', async () => {
  const calls = [];
  const responses = [
    { json: { headerName: 'X-CSRF-TOKEN', token: 'first' }, cookie: 'XSRF-TOKEN=one; Path=/' },
    { json: { username: 'operator' }, cookie: 'JSESSIONID=session-one; HttpOnly' },
    { json: { headerName: 'X-CSRF-TOKEN', token: 'second' } },
    { status: 204 },
  ];
  const client = new DemoClient({ baseUrl: 'http://127.0.0.1:8088', fetchImpl: async (url, options) => {
    calls.push({ path: url.pathname, headers: options.headers, body: options.body });
    const next = responses.shift();
    return { ok: true, status: next.status ?? 200, headers: { getSetCookie: () => next.cookie ? [next.cookie] : [] }, json: async () => next.json };
  } });
  await client.login({ username: 'operator', password: 'private-test-value' });
  await client.logout();
  assert.deepEqual(calls.map(call => call.path), ['/api/auth/csrf', '/api/auth/login', '/api/auth/csrf', '/api/auth/logout']);
  assert.equal(calls[1].headers['X-CSRF-TOKEN'], 'first');
  assert.match(calls[1].headers.Cookie, /XSRF-TOKEN=one/);
  assert.equal(calls[3].headers['X-CSRF-TOKEN'], 'second');
  assert.match(calls[3].headers.Cookie, /JSESSIONID=session-one/);
});
