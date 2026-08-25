import { describe, expect, it } from "vitest";
import { parseEventData, statusTone } from "../src/lib/view";

describe("delivery presentation", () => {
  it("maps terminal states to distinct tones", () => {
    expect(statusTone("SUCCEEDED")).toBe("success");
    expect(statusTone("DEAD")).toBe("danger");
    expect(statusTone("RETRY_SCHEDULED")).toBe("active");
    expect(statusTone("PENDING")).toBe("neutral");
  });

  it("accepts structured JSON event data", () => {
    expect(parseEventData('{"result":"ok"}')).toEqual({ result: "ok" });
    expect(() => parseEventData('"not-an-object"')).toThrow(/object or array/);
  });
});
