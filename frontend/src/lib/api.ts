import type { ApiError, Delivery, DeliveryDetail, DeliveryStats, Endpoint } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiError;
    const fieldMessage = body.fields ? Object.values(body.fields)[0] : undefined;
    throw new Error(fieldMessage ?? body.message ?? `Request failed with HTTP ${response.status}`);
  }

  return (await response.json()) as T;
}

export const api = {
  listEndpoints: () => request<Endpoint[]>("/api/endpoints"),
  createEndpoint: (input: { name: string; url: string; secret: string }) =>
    request<Endpoint>("/api/endpoints", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  setEndpointActive: (id: string, active: boolean, expectedVersion: number) =>
    request<Endpoint>(`/api/endpoints/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active, expectedVersion }),
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
};
