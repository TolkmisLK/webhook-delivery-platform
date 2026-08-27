import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { StatusBadge } from "./components/StatusBadge";
import { api, ApiRequestError } from "./lib/api";
import type { Delivery, DeliveryDetail, DeliveryStats, Endpoint } from "./lib/types";
import {
  canCancelDelivery,
  formatDuration,
  formatTimestamp,
  parseEventData,
  selectActiveEndpoint,
} from "./lib/view";

type Locale = "en" | "zh";
type AuthState =
  | { status: "checking" }
  | { status: "signed-out" }
  | { status: "signed-in"; username: string };

interface EventFormState {
  endpointId: string;
  eventType: string;
  idempotencyKey: string;
  data: string;
}

const copy = {
  en: {
    eyebrow: "NCC · Infrastructure portfolio",
    title: "Webhook Delivery Platform",
    intro: "Durable event delivery with signed requests, bounded retries, and an inspectable audit trail.",
    live: "Live updates",
    reconnecting: "Reconnecting",
    total: "Total deliveries",
    succeeded: "Succeeded",
    retrying: "Retrying",
    dead: "Dead letters",
    canceled: "Canceled",
    endpointTitle: "Register endpoint",
    endpointHelp: "Secrets are encrypted before persistence and never returned by the API.",
    name: "Name",
    targetUrl: "Target URL",
    signingSecret: "Signing secret",
    addEndpoint: "Add endpoint",
    eventTitle: "Publish event",
    eventHelp: "The idempotency key prevents duplicate jobs for the same endpoint.",
    endpoint: "Endpoint",
    eventType: "Event type",
    idempotency: "Idempotency key",
    eventData: "Event data",
    publish: "Publish event",
    recentTitle: "Recent deliveries",
    refresh: "Refresh",
    empty: "No deliveries yet. Register an endpoint and publish the first event.",
    target: "Target",
    attempts: "Attempts",
    response: "Response",
    updated: "Updated",
    action: "Action",
    replay: "Replay",
    cancelDelivery: "Cancel",
    cancelConfirm: "Cancel this queued delivery? It can be replayed later.",
    inspect: "Inspect",
    historyTitle: "Attempt timeline",
    historyHelp: "Committed outcomes only. Signing secrets and request signatures are never stored.",
    close: "Close",
    attempt: "Attempt",
    duration: "Duration",
    result: "Result",
    noAttempts: "No committed attempt yet.",
    registered: "Registered endpoints",
    noEndpoints: "No endpoint registered yet.",
    active: "ACTIVE",
    inactive: "INACTIVE",
    activate: "Activate",
    deactivate: "Deactivate",
    editEndpoint: "Edit",
    editEndpointHelp: "Only deliveries accepted after saving use the updated target URL.",
    updateEndpointConfirm: "Save this Endpoint configuration? Existing deliveries keep their original target URL.",
    saveChanges: "Save changes",
    rotateSecret: "Rotate secret",
    newSigningSecret: "New signing secret",
    rotateSecretHelp: "Keep both secrets active at the receiver until older deliveries finish.",
    rotateSecretConfirm: "Rotate this signing secret? Existing deliveries will keep using the old secret.",
    confirmRotation: "Confirm rotation",
    cancelRotation: "Cancel",
    noActiveEndpoints: "No active endpoint",
    signInEyebrow: "Protected operator console",
    signInTitle: "Sign in to continue",
    signInHelp: "Use the single operator credentials configured for this deployment.",
    username: "Username",
    password: "Password",
    signIn: "Sign in",
    signingIn: "Signing in…",
    checkingSession: "Checking operator session…",
    signedInAs: "Signed in as",
    signOut: "Sign out",
  },
  zh: {
    eyebrow: "NCC · 基础设施公开项目",
    title: "可靠 Webhook 推送平台",
    intro: "通过签名请求、有限重试和可审查记录，可靠推送持久化事件。",
    live: "实时更新",
    reconnecting: "正在重连",
    total: "全部推送",
    succeeded: "推送成功",
    retrying: "等待重试",
    dead: "死信任务",
    canceled: "已取消",
    endpointTitle: "注册 Endpoint",
    endpointHelp: "签名密钥加密后持久化，API 不会返回密钥内容。",
    name: "名称",
    targetUrl: "目标地址",
    signingSecret: "签名密钥",
    addEndpoint: "添加 Endpoint",
    eventTitle: "发布事件",
    eventHelp: "幂等键用于避免同一 Endpoint 生成重复任务。",
    endpoint: "Endpoint",
    eventType: "事件类型",
    idempotency: "幂等键",
    eventData: "事件数据",
    publish: "发布事件",
    recentTitle: "最近推送",
    refresh: "刷新",
    empty: "暂无推送记录。请先注册 Endpoint，再发布第一个事件。",
    target: "目标",
    attempts: "尝试次数",
    response: "响应",
    updated: "更新时间",
    action: "操作",
    replay: "重新投递",
    cancelDelivery: "取消任务",
    cancelConfirm: "确认取消这条排队中的推送任务？之后仍可重新投递。",
    inspect: "查看详情",
    historyTitle: "推送尝试时间线",
    historyHelp: "这里只展示事务已提交的结果；签名密钥和请求签名不会被保存。",
    close: "关闭",
    attempt: "尝试",
    duration: "耗时",
    result: "结果",
    noAttempts: "暂无已提交的推送尝试。",
    registered: "已注册 Endpoint",
    noEndpoints: "暂未注册 Endpoint。",
    active: "已启用",
    inactive: "已停用",
    activate: "启用",
    deactivate: "停用",
    editEndpoint: "编辑",
    editEndpointHelp: "只有保存后新接收的任务使用更新后的目标地址。",
    updateEndpointConfirm: "确认保存 Endpoint 配置？已接收任务仍使用原目标地址。",
    saveChanges: "保存修改",
    rotateSecret: "轮换密钥",
    newSigningSecret: "新签名密钥",
    rotateSecretHelp: "旧任务结束前，请让接收端同时接受新旧密钥。",
    rotateSecretConfirm: "确认轮换签名密钥？已接收任务仍会使用旧密钥。",
    confirmRotation: "确认轮换",
    cancelRotation: "取消",
    noActiveEndpoints: "暂无已启用 Endpoint",
    signInEyebrow: "受保护的运维控制台",
    signInTitle: "登录后继续",
    signInHelp: "请输入当前部署配置的单操作者账号与密码。",
    username: "用户名",
    password: "密码",
    signIn: "登录",
    signingIn: "正在登录…",
    checkingSession: "正在检查运维会话…",
    signedInAs: "当前账号",
    signOut: "退出登录",
  },
} as const;

const emptyStats: DeliveryStats = {
  total: 0,
  byStatus: {
    PENDING: 0,
    PROCESSING: 0,
    RETRY_SCHEDULED: 0,
    SUCCEEDED: 0,
    DEAD: 0,
    CANCELED: 0,
  },
};

export function App() {
  const [locale, setLocale] = useState<Locale>("en");
  const [auth, setAuth] = useState<AuthState>({ status: "checking" });
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [stats, setStats] = useState<DeliveryStats>(emptyStats);
  const [selectedDelivery, setSelectedDelivery] = useState<DeliveryDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [streamConnected, setStreamConnected] = useState(false);
  const [loginForm, setLoginForm] = useState({ username: "admin", password: "" });
  const [endpointForm, setEndpointForm] = useState({
    name: "Local demo receiver",
    url: "http://receiver:8090/hooks",
    secret: "local-demo-secret",
  });
  const [endpointEdit, setEndpointEdit] = useState({ endpointId: "", name: "", url: "" });
  const [secretRotation, setSecretRotation] = useState({ endpointId: "", newSecret: "" });
  const [eventForm, setEventForm] = useState<EventFormState>({
    endpointId: "",
    eventType: "demo.completed",
    idempotencyKey: crypto.randomUUID(),
    data: '{\n  "result": "ok",\n  "source": "console"\n}',
  });
  const t = copy[locale];

  const handleError = useCallback((cause: unknown) => {
    if (cause instanceof ApiRequestError && cause.status === 401) {
      api.resetSessionState();
      setAuth({ status: "signed-out" });
      setStreamConnected(false);
      return;
    }
    setError(messageOf(cause));
  }, []);

  const load = useCallback(async () => {
    try {
      const [nextEndpoints, nextDeliveries, nextStats] = await Promise.all([
        api.listEndpoints(),
        api.listDeliveries(),
        api.getStats(),
      ]);
      setEndpoints(nextEndpoints);
      setDeliveries(nextDeliveries);
      setStats(nextStats);
      setEventForm((current) => ({
        ...current,
        endpointId: selectActiveEndpoint(nextEndpoints, current.endpointId),
      }));
      setError(null);
    } catch (loadError) {
      handleError(loadError);
    }
  }, [handleError]);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        await api.refreshCsrf();
        const session = await api.getSession();
        if (active) setAuth({ status: "signed-in", username: session.username });
      } catch (sessionError) {
        if (!active) return;
        if (sessionError instanceof ApiRequestError && sessionError.status === 401) {
          setAuth({ status: "signed-out" });
        } else {
          setError(messageOf(sessionError));
          setAuth({ status: "signed-out" });
        }
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (auth.status !== "signed-in") return;
    void load();
    const stream = new EventSource("/api/deliveries/stream");
    stream.onopen = () => setStreamConnected(true);
    stream.addEventListener("delivery", () => void load());
    stream.onerror = () => {
      setStreamConnected(false);
      void api.getSession().catch(handleError);
    };
    return () => stream.close();
  }, [auth.status, handleError, load]);

  const statCards = useMemo(
    () => [
      [t.total, stats.total],
      [t.succeeded, stats.byStatus.SUCCEEDED],
      [t.retrying, stats.byStatus.RETRY_SCHEDULED],
      [t.dead, stats.byStatus.DEAD],
      [t.canceled, stats.byStatus.CANCELED],
    ],
    [stats, t],
  );

  async function createEndpoint(event: FormEvent) {
    event.preventDefault();
    await run(async () => {
      const created = await api.createEndpoint(endpointForm);
      setEndpointForm((current) => ({ ...current, secret: "" }));
      setEventForm((current) => ({ ...current, endpointId: created.id }));
      setNotice(locale === "zh" ? "Endpoint 已创建。" : "Endpoint created.");
      await load();
    });
  }

  async function signIn(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = await api.login(loginForm.username, loginForm.password);
      setLoginForm((current) => ({ ...current, password: "" }));
      setAuth({ status: "signed-in", username: session.username });
    } catch (loginError) {
      setError(messageOf(loginError));
    } finally {
      setBusy(false);
    }
  }

  async function signOut() {
    setBusy(true);
    setError(null);
    try {
      await api.logout();
      setAuth({ status: "signed-out" });
      setEndpoints([]);
      setDeliveries([]);
      setStats(emptyStats);
      setSelectedDelivery(null);
      setStreamConnected(false);
    } catch (logoutError) {
      handleError(logoutError);
    } finally {
      setBusy(false);
    }
  }

  async function publishEvent(event: FormEvent) {
    event.preventDefault();
    await run(async () => {
      const result = await api.publishEvent({
        endpointId: eventForm.endpointId,
        eventType: eventForm.eventType,
        idempotencyKey: eventForm.idempotencyKey,
        data: parseEventData(eventForm.data),
      });
      setNotice(result.duplicate
        ? (locale === "zh" ? "幂等键已存在，返回原推送任务。" : "Existing idempotent delivery returned.")
        : (locale === "zh" ? "事件已进入持久化队列。" : "Event accepted into the durable queue."));
      setEventForm((current) => ({ ...current, idempotencyKey: crypto.randomUUID() }));
      await load();
    });
  }

  async function setEndpointActive(endpoint: Endpoint) {
    await run(async () => {
      const updated = await api.setEndpointActive(endpoint.id, !endpoint.active, endpoint.version);
      setNotice(
        locale === "zh"
          ? `Endpoint 已${updated.active ? "启用" : "停用"}。`
          : `Endpoint ${updated.active ? "activated" : "deactivated"}.`,
      );
      await load();
    });
  }

  async function rotateEndpointSecret(event: FormEvent, endpoint: Endpoint) {
    event.preventDefault();
    if (!window.confirm(t.rotateSecretConfirm)) return;
    const newSecret = secretRotation.newSecret;
    setSecretRotation({ endpointId: "", newSecret: "" });
    await run(async () => {
      await api.rotateEndpointSecret(endpoint.id, newSecret, endpoint.version);
      setNotice(locale === "zh" ? "Endpoint 签名密钥已轮换。" : "Endpoint signing secret rotated.");
      await load();
    });
  }

  async function updateEndpoint(event: FormEvent, endpoint: Endpoint) {
    event.preventDefault();
    if (!window.confirm(t.updateEndpointConfirm)) return;
    const { name, url } = endpointEdit;
    setEndpointEdit({ endpointId: "", name: "", url: "" });
    await run(async () => {
      await api.updateEndpoint(endpoint.id, name, url, endpoint.version);
      setNotice(locale === "zh" ? "Endpoint 配置已更新。" : "Endpoint configuration updated.");
      await load();
    });
  }

  async function replay(id: string) {
    await run(async () => {
      await api.replay(id);
      setNotice(locale === "zh" ? "推送任务已重新进入队列。" : "Delivery queued for replay.");
      await load();
    });
  }

  async function cancelDelivery(id: string) {
    if (!window.confirm(t.cancelConfirm)) return;
    await run(async () => {
      await api.cancel(id);
      setNotice(locale === "zh" ? "推送任务已取消。" : "Delivery canceled.");
      await load();
    });
  }

  async function inspect(id: string) {
    await run(async () => {
      setSelectedDelivery(await api.getDelivery(id));
    });
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (actionError) {
      handleError(actionError);
    } finally {
      setBusy(false);
    }
  }

  if (auth.status !== "signed-in") {
    return (
      <div className="app-shell auth-shell">
        <header className="topbar">
          <a className="brand" href="#top">NCC</a>
          <button className="ghost-button" type="button" onClick={() => setLocale(locale === "en" ? "zh" : "en")}>
            {locale === "en" ? "中文" : "EN"}
          </button>
        </header>
        <main id="top" className="auth-main">
          <section className="auth-card" aria-labelledby="auth-title">
            <p className="eyebrow">{t.signInEyebrow}</p>
            <h1 id="auth-title">{t.signInTitle}</h1>
            <p>{auth.status === "checking" ? t.checkingSession : t.signInHelp}</p>
            {error && <div className="message message-error" role="alert">{error}</div>}
            {auth.status === "signed-out" && (
              <form className="auth-form" onSubmit={signIn}>
                <label>{t.username}<input autoFocus required maxLength={120} autoComplete="username" value={loginForm.username} onChange={(event) => setLoginForm({ ...loginForm, username: event.target.value })} /></label>
                <label>{t.password}<input required maxLength={512} type="password" autoComplete="current-password" value={loginForm.password} onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })} /></label>
                <button className="primary-button" disabled={busy} type="submit">{busy ? t.signingIn : t.signIn}</button>
              </form>
            )}
          </section>
        </main>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top">NCC</a>
        <div className="topbar-actions">
          <span className={`live-indicator${streamConnected ? "" : " reconnecting"}`}>
            <i /> {streamConnected ? t.live : t.reconnecting}
          </span>
          <span className="operator-identity">{t.signedInAs} <strong>{auth.username}</strong></span>
          <button className="ghost-button" type="button" onClick={() => setLocale(locale === "en" ? "zh" : "en")}>
            {locale === "en" ? "中文" : "EN"}
          </button>
          <button className="ghost-button" disabled={busy} type="button" onClick={() => void signOut()}>{t.signOut}</button>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <p className="eyebrow">{t.eyebrow}</p>
          <h1>{t.title}</h1>
          <p>{t.intro}</p>
        </section>

        {error && <div className="message message-error" role="alert">{error}</div>}
        {notice && <div className="message message-success" role="status">{notice}</div>}

        <section className="stats-grid" aria-label="Delivery statistics">
          {statCards.map(([label, value]) => (
            <article className="stat-card" key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </article>
          ))}
        </section>

        <section className="workspace-grid">
          <article className="panel">
            <div className="panel-heading">
              <div><p className="section-index">01</p><h2>{t.endpointTitle}</h2></div>
              <p>{t.endpointHelp}</p>
            </div>
            <form onSubmit={createEndpoint} className="form-grid">
              <label>{t.name}<input required maxLength={120} value={endpointForm.name} onChange={(event) => setEndpointForm({ ...endpointForm, name: event.target.value })} /></label>
              <label className="span-2">{t.targetUrl}<input required type="url" value={endpointForm.url} onChange={(event) => setEndpointForm({ ...endpointForm, url: event.target.value })} /></label>
              <label className="span-2">{t.signingSecret}<input required minLength={16} type="password" autoComplete="new-password" value={endpointForm.secret} onChange={(event) => setEndpointForm({ ...endpointForm, secret: event.target.value })} /></label>
              <button className="primary-button" disabled={busy} type="submit">{t.addEndpoint}</button>
            </form>
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div><p className="section-index">02</p><h2>{t.eventTitle}</h2></div>
              <p>{t.eventHelp}</p>
            </div>
            <form onSubmit={publishEvent} className="form-grid">
              <label>{t.endpoint}<select required value={eventForm.endpointId} onChange={(event) => setEventForm({ ...eventForm, endpointId: event.target.value })}><option value="" disabled>{endpoints.some((endpoint) => endpoint.active) ? t.endpoint : t.noActiveEndpoints}</option>{endpoints.filter((endpoint) => endpoint.active).map((endpoint) => <option value={endpoint.id} key={endpoint.id}>{endpoint.name}</option>)}</select></label>
              <label>{t.eventType}<input required maxLength={160} value={eventForm.eventType} onChange={(event) => setEventForm({ ...eventForm, eventType: event.target.value })} /></label>
              <label className="span-2">{t.idempotency}<input required maxLength={200} value={eventForm.idempotencyKey} onChange={(event) => setEventForm({ ...eventForm, idempotencyKey: event.target.value })} /></label>
              <label className="span-2">{t.eventData}<textarea required rows={5} value={eventForm.data} onChange={(event) => setEventForm({ ...eventForm, data: event.target.value })} /></label>
              <button className="primary-button" disabled={busy || endpoints.length === 0} type="submit">{t.publish}</button>
            </form>
          </article>
        </section>

        <section className="panel table-panel">
          <div className="table-heading"><div><p className="section-index">03</p><h2>{t.recentTitle}</h2></div><button className="ghost-button" type="button" onClick={() => void load()}>{t.refresh}</button></div>
          {deliveries.length === 0 ? <p className="empty-state">{t.empty}</p> : (
            <div className="table-wrap"><table><thead><tr><th>Event</th><th>{t.target}</th><th>Status</th><th>{t.attempts}</th><th>{t.response}</th><th>{t.updated}</th><th>{t.action}</th></tr></thead>
              <tbody>{deliveries.map((delivery) => <tr key={delivery.id}><td><strong>{delivery.eventType}</strong><code>{delivery.eventId.slice(0, 8)}</code></td><td><strong>{delivery.endpointName}</strong><small title={delivery.endpointUrl}>{delivery.endpointUrl}</small></td><td><StatusBadge status={delivery.status} /></td><td>{delivery.attemptCount} / {delivery.maxAttempts}</td><td>{delivery.lastStatusCode ?? delivery.lastError ?? "—"}</td><td>{formatTimestamp(delivery.updatedAt, locale)}</td><td><div className="row-actions"><button className="text-button" disabled={busy} type="button" onClick={() => void inspect(delivery.id)}>{t.inspect}</button><button className="text-button" disabled={busy || delivery.status === "PROCESSING"} type="button" onClick={() => void replay(delivery.id)}>{t.replay}</button>{canCancelDelivery(delivery.status) && <button className="text-button danger-action" disabled={busy} type="button" onClick={() => void cancelDelivery(delivery.id)}>{t.cancelDelivery}</button>}</div></td></tr>)}</tbody>
            </table></div>
          )}
          {selectedDelivery && (
            <section className="attempt-detail" aria-labelledby="attempt-detail-title">
              <div className="attempt-detail-heading">
                <div>
                  <p className="section-index">{selectedDelivery.delivery.id.slice(0, 8)}</p>
                  <h3 id="attempt-detail-title">{t.historyTitle}</h3>
                  <p>{t.historyHelp}</p>
                </div>
                <button className="ghost-button" type="button" onClick={() => setSelectedDelivery(null)}>{t.close}</button>
              </div>
              {selectedDelivery.attempts.length === 0 ? <p className="empty-state">{t.noAttempts}</p> : (
                <ol className="attempt-timeline">
                  {selectedDelivery.attempts.map((attempt) => (
                    <li key={attempt.attemptNumber}>
                      <div className="attempt-marker">{attempt.attemptNumber}</div>
                      <div className="attempt-content">
                        <div className="attempt-summary"><strong>{t.attempt} {attempt.attemptNumber}</strong><StatusBadge status={attempt.outcome} /></div>
                        <dl>
                          <div><dt>HTTP</dt><dd>{attempt.statusCode ?? "—"}</dd></div>
                          <div><dt>{t.duration}</dt><dd>{formatDuration(attempt.durationMs)}</dd></div>
                          <div><dt>{t.updated}</dt><dd>{formatTimestamp(attempt.finishedAt, locale)}</dd></div>
                          <div className="attempt-result"><dt>{t.result}</dt><dd>{attempt.errorMessage ?? attempt.responseExcerpt ?? "—"}</dd></div>
                        </dl>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          )}
        </section>

        <section className="panel endpoint-list">
          <div><p className="section-index">04</p><h2>{t.registered}</h2></div>
          {endpoints.length === 0 ? <p>{t.noEndpoints}</p> : (
            <ul>{endpoints.map((endpoint) => (
              <li key={endpoint.id}>
                <div className="endpoint-row">
                  <span><strong>{endpoint.name}</strong><small>{endpoint.url}</small></span>
                  <span className="endpoint-actions">
                    <span className={`endpoint-state${endpoint.active ? "" : " inactive"}`}>{endpoint.active ? t.active : t.inactive}</span>
                    <button className="text-button" disabled={busy} type="button" onClick={() => void setEndpointActive(endpoint)}>{endpoint.active ? t.deactivate : t.activate}</button>
                    <button className="text-button" disabled={busy} type="button" onClick={() => { setEndpointEdit({ endpointId: endpoint.id, name: endpoint.name, url: endpoint.url }); setSecretRotation({ endpointId: "", newSecret: "" }); }}>{t.editEndpoint}</button>
                    <button className="text-button" disabled={busy} type="button" onClick={() => { setSecretRotation({ endpointId: endpoint.id, newSecret: "" }); setEndpointEdit({ endpointId: "", name: "", url: "" }); }}>{t.rotateSecret}</button>
                  </span>
                </div>
                {endpointEdit.endpointId === endpoint.id && (
                  <form className="endpoint-edit-form" onSubmit={(event) => void updateEndpoint(event, endpoint)}>
                    <label>{t.name}<input autoFocus required maxLength={120} value={endpointEdit.name} onChange={(event) => setEndpointEdit({ ...endpointEdit, name: event.target.value })} /></label>
                    <label>{t.targetUrl}<input required type="url" maxLength={2048} value={endpointEdit.url} onChange={(event) => setEndpointEdit({ ...endpointEdit, url: event.target.value })} /></label>
                    <p>{t.editEndpointHelp}</p>
                    <span className="configuration-actions"><button className="primary-button" disabled={busy} type="submit">{t.saveChanges}</button><button className="ghost-button" disabled={busy} type="button" onClick={() => setEndpointEdit({ endpointId: "", name: "", url: "" })}>{t.cancelRotation}</button></span>
                  </form>
                )}
                {secretRotation.endpointId === endpoint.id && <form className="secret-rotation-form" onSubmit={(event) => void rotateEndpointSecret(event, endpoint)}><label>{t.newSigningSecret}<input autoFocus required minLength={16} maxLength={512} type="password" autoComplete="new-password" value={secretRotation.newSecret} onChange={(event) => setSecretRotation({ endpointId: endpoint.id, newSecret: event.target.value })} /></label><p>{t.rotateSecretHelp}</p><span className="rotation-actions"><button className="primary-button" disabled={busy} type="submit">{t.confirmRotation}</button><button className="ghost-button" disabled={busy} type="button" onClick={() => setSecretRotation({ endpointId: "", newSecret: "" })}>{t.cancelRotation}</button></span></form>}
              </li>
            ))}</ul>
          )}
        </section>
      </main>

      <footer><span>© 2026 NCC</span><span>At-least-once delivery · Database-backed queue · HMAC-SHA256</span></footer>
    </div>
  );
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "Unexpected error";
}
