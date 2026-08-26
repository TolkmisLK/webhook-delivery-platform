import { describe, expect, it } from "vitest";
import { canCancelDelivery, formatDuration, parseEventData, statusTone } from "../src/lib/view";

describe("delivery presentation", () => {
  it("maps terminal states to distinct tones", () => {
    expect(statusTone("SUCCEEDED")).toBe("success");
    expect(statusTone("DEAD")).toBe("danger");
    expect(statusTone("RETRY_SCHEDULED")).toBe("active");
    expect(statusTone("PENDING")).toBe("neutral");
    expect(statusTone("CANCELED")).toBe("neutral");
  });

  it("allows cancellation only before outbound work starts", () => {
    expect(canCancelDelivery("PENDING")).toBe(true);
    expect(canCancelDelivery("RETRY_SCHEDULED")).toBe(true);
    expect(canCancelDelivery("PROCESSING")).toBe(false);
    expect(canCancelDelivery("SUCCEEDED")).toBe(false);
    expect(canCancelDelivery("DEAD")).toBe(false);
    expect(canCancelDelivery("CANCELED")).toBe(false);
  });

  it("accepts structured JSON event data", () => {
    expect(parseEventData('{"result":"ok"}')).toEqual({ result: "ok" });
    expect(() => parseEventData('"not-an-object"')).toThrow(/object or array/);
  });

  it("formats attempt duration without hiding slow deliveries", () => {
    expect(formatDuration(125)).toBe("125 ms");
    expect(formatDuration(1500)).toBe("1.5 s");
    expect(formatDuration(12_000)).toBe("12 s");
  });
});
