import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Pager } from "./pager";
import { StatusTabs } from "./status-tabs";

afterEach(() => {
  cleanup();
});

describe("목록 조건", () => {
  it("쪽을 넘겨도 상태와 다른 조건(판매자 필터)을 남긴다", () => {
    render(
      <Pager
        basePath="/products"
        status="ON_SALE"
        page={1}
        size={20}
        totalCount={60}
        query={{ sellerId: "s-1", empty: undefined }}
      />,
    );

    expect(screen.getByRole("link", { name: "이전" })).toHaveAttribute(
      "href",
      "/products?sellerId=s-1&status=ON_SALE&page=0",
    );
    expect(screen.getByRole("link", { name: "다음" })).toHaveAttribute(
      "href",
      "/products?sellerId=s-1&status=ON_SALE&page=2",
    );
  });

  it("상태 탭을 바꿔도 다른 조건을 남기고, 전체 탭은 상태를 뺀다", () => {
    render(
      <StatusTabs
        basePath="/products"
        current="BANNED"
        query={{ sellerId: "s-1" }}
        tabs={[
          { status: undefined, label: "전체" },
          { status: "BANNED", label: "판매 제한" },
        ]}
      />,
    );

    expect(screen.getByRole("link", { name: "전체" })).toHaveAttribute(
      "href",
      "/products?sellerId=s-1",
    );
    expect(screen.getByRole("link", { name: "판매 제한" })).toHaveAttribute(
      "href",
      "/products?sellerId=s-1&status=BANNED",
    );
  });
});
