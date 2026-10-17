// 앱 서버(BFF)가 떠 있는지 확인한다. Compose healthcheck와 CI가 쓴다.
export const dynamic = "force-dynamic";

export function GET(): Response {
  return Response.json({ status: "UP", app: "admin" });
}
