import assert from 'node:assert/strict';

export class DemoClient {
  constructor({ baseUrl = process.env.DEMO_BASE_URL ?? 'http://localhost:8088', fetchImpl = fetch } = {}) {
    this.origin = new URL(baseUrl);
    assert.ok(
      this.origin.protocol === 'http:' && ['localhost', '127.0.0.1', 'frontend'].includes(this.origin.hostname),
      'DEMO_BASE_URL must point to the local Compose demo',
    );
    assert.ok(!this.origin.username && !this.origin.password && this.origin.pathname === '/' && !this.origin.search && !this.origin.hash,
      'Use an origin without credentials, a path, query, or fragment');
    this.fetchImpl = fetchImpl;
    this.cookies = new Map();
    this.csrf = undefined;
  }

  async request(path, body) {
    const response = await this.fetchImpl(new URL(path, this.origin), {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: [...this.cookies].map(([key, value]) => `${key}=${value}`).join('; '),
        ...(body !== undefined && this.csrf ? { [this.csrf.headerName]: this.csrf.token } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'error',
      signal: AbortSignal.timeout(10000),
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0];
      const separator = pair.indexOf('=');
      this.cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
    if (!response.ok) {
      const error = new Error(`${path}: HTTP ${response.status}. Check the demo settings and docs/demo.md.`);
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? undefined : response.json();
  }

  async login({ username = process.env.APP_OPERATOR_USERNAME ?? 'admin', password = process.env.APP_OPERATOR_PASSWORD ?? 'local-admin-password' } = {}) {
    this.csrf = await this.request('/api/auth/csrf');
    await this.request('/api/auth/login', { username, password });
    this.csrf = await this.request('/api/auth/csrf');
  }

  async logout() {
    if (this.csrf) await this.request('/api/auth/logout', {});
  }
}
