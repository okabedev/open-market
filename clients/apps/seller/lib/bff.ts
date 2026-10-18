import { createNextBff } from "@openmarket/bff/next";

/** 이 앱의 BFF 세션. 서버 컴포넌트, Server Action, Route Handler에서 쓴다(clients/CLAUDE.md). */
export const bff = createNextBff("seller");
