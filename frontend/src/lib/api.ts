import type {
  ApiError,
  CsrfMetadata,
  Delivery,
  DeliveryDetail,
  DeliveryStats,
  Endpoint,
  OperatorSession,
} from "./types";

let csrf: CsrfMetadata | null = null;

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

async function refreshCsrf(): Promise<CsrfMetadata> {
  const response = await fetch("/api/auth/csrf", {
    headers: { "Content-Type": "application/json" },
  });
  if (!response.ok) throw await errorFrom(response);
  csrf = (await response.json()) as CsrfMetadata;
  return csrf;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() ?? "GET";
  const unsafe = !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method);
  if (unsafe && !csrf) await refreshCsrf();
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(unsafe && csrf ? { [csrf.headerName]: csrf.token } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) throw await errorFrom(response);
  if (response.status === 204) return undefined as T;

  return (await response.json()) as T;
}

async function errorFrom(response: Response): Promise<ApiRequestError> {
  const body = (await response.json().catch(() => ({}))) as ApiError;
  const fieldMessage = body.fields ? Object.values(body.fields)[0] : undefined;
  return new ApiRequestError(
    fieldMessage ?? body.message ?? `Request failed with HTTP ${response.status}`,
    response.status,
    body.code,
  );
}

export const api = {
  resetSessionState: () => {
    csrf = null;
  },
  refreshCsrf,
  getSession: () => request<OperatorSession>("/api/auth/session"),
  login: async (username: string, password: string) => {
    await refreshCsrf();
    const session = await request<OperatorSession>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
    csrf = null;
    await refreshCsrf();
    return session;
  },
  logout: async () => {
    await request<void>("/api/auth/logout", { method: "POST" });
    csrf = null;
  },
  listEndpoints: () => request<Endpoint[]>("/api/endpoints"),
  createEndpoint: (input: { name: string; url: string; secret: string }) =>
    request<Endpoint>("/api/endpoints", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateEndpoint: (id: string, name: string, url: string, expectedVersion: number) =>
    request<Endpoint>(`/api/endpoints/${id}`, {
      method: "PUT",
      body: JSON.stringify({ name, url, expectedVersion }),
    }),
  setEndpointActive: (id: string, active: boolean, expectedVersion: number) =>
    request<Endpoint>(`/api/endpoints/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active, expectedVersion }),
    }),
  rotateEndpointSecret: (id: string, newSecret: string, expectedVersion: number) =>
    request<Endpoint>(`/api/endpoints/${id}/secret`, {
      method: "PATCH",
      body: JSON.stringify({ newSecret, expectedVersion }),
    }),
  publishEvent: (input: {
    endpointId: string;
    eventType: string;
    idempotencyKey: string;
    data: unknown;
  }) =>
    request<{ eventId: string; deliveryId: string; duplicate: boolean }>("/api/events", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  listDeliveries: () => request<Delivery[]>("/api/deliveries?limit=50"),
  getDelivery: (id: string) => request<DeliveryDetail>(`/api/deliveries/${id}`),
  getStats: () => request<DeliveryStats>("/api/deliveries/stats"),
  replay: (id: string) =>
    request<Delivery>(`/api/deliveries/${id}/replay`, { method: "POST" }),
  cancel: (id: string) =>
    request<Delivery>(`/api/deliveries/${id}/cancel`, { method: "POST" }),
};
