import { describe, expect, it } from "vitest";
import { formatDate, formatExpectedPosition, formatPercent, formatPoints, positionLabel } from "./format";

describe("formatPercent", () => {
  it("shows one decimal place", () => {
    expect(formatPercent(0.206)).toBe("20.6%");
    expect(formatPercent(0)).toBe("0.0%");
  });
  it("shows a dash for non-finite values", () => {
    expect(formatPercent(Number.NaN)).toBe("-");
  });
});

describe("formatPoints", () => {
  it("keeps whole numbers whole and half points exact", () => {
    expect(formatPoints(437)).toBe("437");
    expect(formatPoints(437.0)).toBe("437");
    expect(formatPoints(0.5)).toBe("0.5");
    expect(formatPoints(10.5)).toBe("10.5");
  });
});

describe("positionLabel", () => {
  it("uses the position when classified, else the provider code", () => {
    expect(positionLabel(3, "3")).toBe("3");
    expect(positionLabel(null, "R")).toBe("R");
    expect(positionLabel(null, "D")).toBe("D");
  });
});

describe("formatDate", () => {
  it("formats an ISO date without shifting the day", () => {
    expect(formatDate("2026-10-11")).toBe("11 Oct 2026");
    expect(formatDate("2026-10-25")).toBe("25 Oct 2026");
  });
});

describe("formatExpectedPosition", () => {
  it("uses two decimal places", () => {
    expect(formatExpectedPosition(3.81)).toBe("3.81");
  });
});
