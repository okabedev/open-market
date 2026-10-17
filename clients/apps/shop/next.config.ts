import type { NextConfig } from "next";

// shop(구매자) 앱. 워크스페이스 패키지는 TypeScript 원본을 그대로 내보내므로 Next.js가 함께 컴파일한다.
const nextConfig: NextConfig = {
  transpilePackages: ["@openmarket/ui"],
};

export default nextConfig;
