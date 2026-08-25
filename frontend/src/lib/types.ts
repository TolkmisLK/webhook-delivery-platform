export type DeliveryStatus =
  | "PENDING"
  | "PROCESSING"
  | "RETRY_SCHEDULED"
  | "SUCCEEDED"
  | "DEAD";

export interface Endpoint {
  id: string;
  name: string;
  url: string;
  active: boolean;
  createdAt: string;
}

export interface Delivery {
  id: string;
  eventId: string;
  eventType: string;
  endpointName: string;
  endpointUrl: string;
  status: DeliveryStatus;
  attemptCount: number;
  maxAttempts: number;
  nextAttemptAt: string;
  lastStatusCode: number | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeliveryStats {
  total: number;
  byStatus: Record<DeliveryStatus, number>;
}

export interface DeliveryAttempt {
  attemptNumber: number;
  outcome: DeliveryStatus;
  statusCode: number | null;
  errorMessage: string | null;
  responseExcerpt: string | null;
  durationMs: number;
  startedAt: string;
  finishedAt: string;
}

export interface DeliveryDetail {
  delivery: Delivery;
  attempts: DeliveryAttempt[];
}

export interface ApiError {
  message?: string;
  fields?: Record<string, string>;
  requestId?: string;
}
