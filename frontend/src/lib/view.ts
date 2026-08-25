import type { DeliveryStatus } from "./types";

export function formatTimestamp(value: string, locale: "en" | "zh"): string {
  return new Intl.DateTimeFormat(locale === "zh" ? "zh-CN" : "en-US", {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
}

export function statusTone(status: DeliveryStatus): "neutral" | "active" | "success" | "danger" {
  if (status === "SUCCEEDED") return "success";
  if (status === "DEAD") return "danger";
  if (status === "PROCESSING" || status === "RETRY_SCHEDULED") return "active";
  return "neutral";
}

export function parseEventData(value: string): unknown {
  const parsed: unknown = JSON.parse(value);
  if (parsed === null || typeof parsed !== "object") {
    throw new Error("Event data must be a JSON object or array");
  }
  return parsed;
}
