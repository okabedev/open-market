import type { NextConfig } from "next";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080";

// seller(판매자) 앱. 워크스페이스 패키지는 TypeScript 원본을 그대로 내보내므로 Next.js가 함께 컴파일한다.
const nextConfig: NextConfig = {
  transpilePackages: ["@openmarket/ui", "@openmarket/bff", "@openmarket/api-client"],
  // 공개 이미지 변형(docs/architecture/file.md "썸네일과 제공")을 같은 Origin으로 받는다. 브라우저는 gateway를 직접 부르지 않는다.
  async rewrites() {
    return [{ source: "/files/:path*", destination: `${GATEWAY_URL}/files/:path*` }];
  },
};

export default nextConfig;
