// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SearchBox } from "./search-box";

describe("SearchBox", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    fetchMock.mockResolvedValue(Response.json({ items: ["반팔 티셔츠", "반팔 셔츠"] }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function options(): string[] {
    return Array.from(document.querySelectorAll("#q-suggestions option")).map(
      (option) => (option as HTMLOptionElement).value,
    );
  }

  it("입력을 멈추고 300ms 뒤 BFF 자동완성을 한 번 부르고 목록을 채운다", async () => {
    render(<SearchBox defaultValue="" />);
    const input = screen.getByRole("combobox", { name: "검색어" });

    fireEvent.change(input, { target: { value: "반" } });
    fireEvent.change(input, { target: { value: "반팔" } });
    await act(() => vi.advanceTimersByTimeAsync(299));
    expect(fetchMock).not.toHaveBeenCalled();

    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/suggestions?q=%EB%B0%98%ED%8C%94");
    expect(options()).toEqual(["반팔 티셔츠", "반팔 셔츠"]);
    expect(input).toHaveAttribute("list", "q-suggestions");
  });

  it("검색어를 지우면 부르지 않고 목록을 비운다", async () => {
    render(<SearchBox defaultValue="" />);
    const input = screen.getByRole("combobox", { name: "검색어" });
    fireEvent.change(input, { target: { value: "반팔" } });
    await act(() => vi.advanceTimersByTimeAsync(300));
    expect(options()).toHaveLength(2);

    fireEvent.change(input, { target: { value: "  " } });
    await act(() => vi.advanceTimersByTimeAsync(300));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(options()).toEqual([]);
  });

  it("자동완성이 실패해도 검색창은 그대로다", async () => {
    fetchMock.mockRejectedValue(new Error("down"));
    render(<SearchBox defaultValue="티셔츠" />);
    const input = screen.getByRole("combobox", { name: "검색어" });
    expect(input).toHaveValue("티셔츠");

    fireEvent.change(input, { target: { value: "티셔" } });
    await act(() => vi.advanceTimersByTimeAsync(300));
    expect(options()).toEqual([]);
    expect(input).toHaveValue("티셔");
  });
});
