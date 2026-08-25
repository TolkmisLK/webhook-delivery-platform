import type { DeliveryStatus } from "../lib/types";
import { statusTone } from "../lib/view";

export function StatusBadge({ status }: { status: DeliveryStatus }) {
  return <span className={`status status-${statusTone(status)}`}>{status.replace("_", " ")}</span>;
}
