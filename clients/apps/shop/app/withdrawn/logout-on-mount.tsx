"use client";

import { Button } from "@openmarket/ui";
import { useEffect, useRef } from "react";

/** 화면을 열면 로그아웃 폼을 바로 제출한다. JS가 없으면 버튼을 누른다. */
export function LogoutOnMount() {
  const form = useRef<HTMLFormElement>(null);
  useEffect(() => {
    form.current?.requestSubmit();
  }, []);
  return (
    <form ref={form} action="/auth/logout" method="post">
      <Button type="submit">확인</Button>
    </form>
  );
}
