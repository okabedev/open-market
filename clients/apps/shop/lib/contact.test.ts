import { describe, expect, it } from "vitest";
import { phoneField } from "./contact";

describe("phoneField", () => {
  it("member와 같은 규칙으로 전화번호를 받는다", () => {
    for (const phone of ["010-1234-5678", "02-123-4567", "031-1234-5678"]) {
      expect(phoneField.safeParse(phone).success).toBe(true);
    }
    for (const phone of ["01012345678", "1588-1234", "010-12345-6789"]) {
      expect(phoneField.safeParse(phone).success).toBe(false);
    }
  });
});
