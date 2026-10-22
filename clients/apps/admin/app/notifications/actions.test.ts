import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));
const revalidatePath = vi.fn();
vi.mock("next/cache", () => ({
  revalidatePath: (path: string, type: string) => revalidatePath(path, type),
}));

const { openNotification, readAllNotifications } = await import("./actions");

const ID = "0199f5a0-0000-7000-8000-0000000000a1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

beforeEach(() => {
  post.mockReset();
  post.mockResolvedValue({ response: new Response(null, { status: 204 }) });
});

describe("openNotification", () => {
  it("읽음으로 하고 알림의 화면으로 간다", async () => {
    await expect(openNotification(form({ notificationId: ID, href: "/orders/x" }))).rejects.toThrow(
      "redirect:/orders/x",
    );
    expect(revalidatePath).toHaveBeenCalledWith("/", "layout");
    expect(post).toHaveBeenCalledWith("/v1/admin/notifications/{notificationId}/read", {
      params: { path: { notificationId: ID } },
    });
  });

  it("이 앱 밖의 주소로는 보내지 않는다", async () => {
    await expect(
      openNotification(form({ notificationId: ID, href: "https://evil.example" })),
    ).rejects.toThrow("redirect:/notifications");
  });
});

describe("readAllNotifications", () => {
  it("모두 읽음으로 하고 알림함으로 돌아간다", async () => {
    await expect(readAllNotifications()).rejects.toThrow("redirect:/notifications");
    expect(revalidatePath).toHaveBeenCalledWith("/", "layout");
    expect(post).toHaveBeenCalledWith("/v1/admin/notifications/read-all");
  });
});
