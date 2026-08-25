import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { StatusBadge } from "./components/StatusBadge";
import { api } from "./lib/api";
import type { Delivery, DeliveryDetail, DeliveryStats, Endpoint } from "./lib/types";
import { formatDuration, formatTimestamp, parseEventData } from "./lib/view";

type Locale = "en" | "zh";

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
  },
  zh: {
    eyebrow: "NCC · 基础设施公开项目",
    title: "可靠 Webhook 投递平台",
    intro: "通过签名请求、有限重试和可审查记录，完成持久化事件投递。",
    live: "实时更新",
    reconnecting: "正在重连",
    total: "全部投递",
    succeeded: "投递成功",
    retrying: "等待重试",
    dead: "死信任务",
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
    recentTitle: "最近投递",
    refresh: "刷新",
    empty: "暂无投递记录。请先注册 Endpoint，再发布第一个事件。",
    target: "目标",
    attempts: "尝试次数",
    response: "响应",
    updated: "更新时间",
    action: "操作",
    replay: "重新投递",
    inspect: "查看详情",
    historyTitle: "投递尝试时间线",
    historyHelp: "这里只展示事务已提交的结果；签名密钥和请求签名不会被保存。",
    close: "关闭",
    attempt: "尝试",
    duration: "耗时",
    result: "结果",
    noAttempts: "暂无已提交的投递尝试。",
    registered: "已注册 Endpoint",
    noEndpoints: "暂未注册 Endpoint。",
  },
} as const;

const emptyStats: DeliveryStats = {
  total: 0,
  byStatus: { PENDING: 0, PROCESSING: 0, RETRY_SCHEDULED: 0, SUCCEEDED: 0, DEAD: 0 },
};

export function App() {
  const [locale, setLocale] = useState<Locale>("en");
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [stats, setStats] = useState<DeliveryStats>(emptyStats);
  const [selectedDelivery, setSelectedDelivery] = useState<DeliveryDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [streamConnected, setStreamConnected] = useState(false);
  const [endpointForm, setEndpointForm] = useState({
    name: "Local demo receiver",
    url: "http://receiver:8090/hooks",
    secret: "local-demo-secret",
  });
  const [eventForm, setEventForm] = useState<EventFormState>({
    endpointId: "",
    eventType: "demo.completed",
    idempotencyKey: crypto.randomUUID(),
    data: '{\n  "result": "ok",\n  "source": "console"\n}',
  });
  const t = copy[locale];

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
        endpointId: current.endpointId || nextEndpoints[0]?.id || "",
      }));
      setError(null);
    } catch (loadError) {
      setError(messageOf(loadError));
    }
  }, []);

  useEffect(() => {
    void load();
    const stream = new EventSource("/api/deliveries/stream");
    stream.onopen = () => setStreamConnected(true);
    stream.addEventListener("delivery", () => void load());
    stream.onerror = () => setStreamConnected(false);
    return () => stream.close();
  }, [load]);

  const statCards = useMemo(
    () => [
      [t.total, stats.total],
      [t.succeeded, stats.byStatus.SUCCEEDED],
      [t.retrying, stats.byStatus.RETRY_SCHEDULED],
      [t.dead, stats.byStatus.DEAD],
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
        ? (locale === "zh" ? "幂等键已存在，返回原投递任务。" : "Existing idempotent delivery returned.")
        : (locale === "zh" ? "事件已进入持久化队列。" : "Event accepted into the durable queue."));
      setEventForm((current) => ({ ...current, idempotencyKey: crypto.randomUUID() }));
      await load();
    });
  }

  async function replay(id: string) {
    await run(async () => {
      await api.replay(id);
      setNotice(locale === "zh" ? "投递已重新进入队列。" : "Delivery queued for replay.");
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
      setError(messageOf(actionError));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top">NCC</a>
        <div className="topbar-actions">
          <span className={`live-indicator${streamConnected ? "" : " reconnecting"}`}>
            <i /> {streamConnected ? t.live : t.reconnecting}
          </span>
          <button className="ghost-button" type="button" onClick={() => setLocale(locale === "en" ? "zh" : "en")}>
            {locale === "en" ? "中文" : "EN"}
          </button>
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
              <label>{t.endpoint}<select required value={eventForm.endpointId} onChange={(event) => setEventForm({ ...eventForm, endpointId: event.target.value })}><option value="" disabled>{t.endpoint}</option>{endpoints.map((endpoint) => <option value={endpoint.id} key={endpoint.id}>{endpoint.name}</option>)}</select></label>
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
              <tbody>{deliveries.map((delivery) => <tr key={delivery.id}><td><strong>{delivery.eventType}</strong><code>{delivery.eventId.slice(0, 8)}</code></td><td><strong>{delivery.endpointName}</strong><small title={delivery.endpointUrl}>{delivery.endpointUrl}</small></td><td><StatusBadge status={delivery.status} /></td><td>{delivery.attemptCount} / {delivery.maxAttempts}</td><td>{delivery.lastStatusCode ?? delivery.lastError ?? "—"}</td><td>{formatTimestamp(delivery.updatedAt, locale)}</td><td><div className="row-actions"><button className="text-button" disabled={busy} type="button" onClick={() => void inspect(delivery.id)}>{t.inspect}</button><button className="text-button" disabled={busy || delivery.status === "PROCESSING"} type="button" onClick={() => void replay(delivery.id)}>{t.replay}</button></div></td></tr>)}</tbody>
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
          {endpoints.length === 0 ? <p>{t.noEndpoints}</p> : <ul>{endpoints.map((endpoint) => <li key={endpoint.id}><span><strong>{endpoint.name}</strong><small>{endpoint.url}</small></span><span className="endpoint-state">{endpoint.active ? "ACTIVE" : "INACTIVE"}</span></li>)}</ul>}
        </section>
      </main>

      <footer><span>© 2026 NCC</span><span>At-least-once delivery · Database-backed queue · HMAC-SHA256</span></footer>
    </div>
  );
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "Unexpected error";
}
