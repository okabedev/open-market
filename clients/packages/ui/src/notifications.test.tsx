import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationBell, NotificationList } from "./notifications";

let pathname = "/orders";
vi.mock("next/navigation", () => ({ usePathname: () => pathname }));

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  it("처음에는 서버가 읽은 안 읽은 수를 보이고, 주기적으로 다시 읽는다", async () => {
    const fetch = vi.fn(async () => Response.json({ count: 12 }));
    vi.stubGlobal("fetch", fetch);

    render(
      <NotificationBell
        href="/notifications"
        count={3}
        countUrl="/api/notifications/unread-count"
        intervalMs={60_000}
      />,
    );
    expect(screen.getByRole("link", { name: "알림 3개 안 읽음" })).toHaveAttribute(
      "href",
      "/notifications",
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(fetch).toHaveBeenCalledWith("/api/notifications/unread-count", { cache: "no-store" });
    expect(screen.getByRole("link", { name: "알림 12개 안 읽음" })).toHaveTextContent("9+");
  });

  it("안 읽은 알림이 없으면 숫자를 보이지 않고, 다시 읽지 못하면 이전 수를 둔다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("offline");
      }),
    );

    render(<NotificationBell href="/notifications" count={0} countUrl="/x" intervalMs={1_000} />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });

    expect(screen.getByRole("link", { name: "알림" })).not.toHaveTextContent(/\d/);
  });
  it("서버가 새 수를 주면 따라가고, 화면 경로가 바뀌면 다시 읽는다", async () => {
    const fetch = vi.fn(async () => Response.json({ count: 2 }));
    vi.stubGlobal("fetch", fetch);
    const bell = (count: number) => (
      <NotificationBell href="/notifications" count={count} countUrl="/c" intervalMs={60_000} />
    );

    const { rerender } = render(bell(5));
    expect(fetch).not.toHaveBeenCalled();
    rerender(bell(0));
    expect(screen.getByRole("link", { name: "알림" })).toBeInTheDocument();

    pathname = "/claims";
    await act(async () => {
      rerender(bell(0));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("link", { name: "알림 2개 안 읽음" })).toBeInTheDocument();
  });
});

describe("NotificationList", () => {
  const open = vi.fn(async () => {});
  const readAll = vi.fn(async () => {});

  it("알림마다 문구·시각·읽음 여부와 여는 폼을 보이고, 다음 쪽 링크를 단다", () => {
    render(
      <NotificationList
        items={[
          {
            notificationId: "n-1",
            message: "주문하신 상품이 발송됐습니다.",
            createdAt: "2026-10-22T01:00:00Z",
            read: false,
            href: "/orders/o-1",
          },
          {
            notificationId: "n-2",
            message: "입점 신청이 승인됐습니다.",
            createdAt: "2026-10-21T01:00:00Z",
            read: true,
            href: null,
          },
        ]}
        nextHref="/notifications?cursor=c-1"
        openAction={open}
        readAllAction={readAll}
      />,
    );

    const unread = screen.getByRole("listitem", { name: /주문하신 상품이 발송됐습니다/ });
    expect(unread).toHaveTextContent("새 알림");
    expect(
      screen.getByRole("button", { name: "주문하신 상품이 발송됐습니다. 보기" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "입점 신청이 승인됐습니다. 읽음" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "모두 읽음" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "더 보기" })).toHaveAttribute(
      "href",
      "/notifications?cursor=c-1",
    );
  });

  it("알림이 없으면 안내만 보인다", () => {
    render(
      <NotificationList items={[]} nextHref={null} openAction={open} readAllAction={readAll} />,
    );

    expect(screen.getByText("알림이 없습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "모두 읽음" })).not.toBeInTheDocument();
  });
});
