"use client";

import { Input, Label } from "@openmarket/ui";
import { useEffect, useState } from "react";
import { SUGGESTION_DEBOUNCE_MS, suggestionKeyword } from "@/lib/suggestions";

const LIST_ID = "q-suggestions";

/**
 * 검색어 입력과 자동완성(SRC-2). 입력을 멈추고 300ms 뒤 BFF `/api/suggestions`를 불러 브라우저 기본 목록(`datalist`)에 넣는다. 키보드
 * 선택과 화면 읽기는 브라우저가 맡는다. 자동완성이 없거나 실패해도 폼은 JS 없이 검색한다.
 */
export function SearchBox({ defaultValue }: { defaultValue?: string }) {
  const [value, setValue] = useState(defaultValue ?? "");
  const [suggestions, setSuggestions] = useState<string[]>([]);

  useEffect(() => {
    const keyword = suggestionKeyword(value);
    if (!keyword || keyword === defaultValue) {
      // 지운 검색어나 이미 검색한 검색어는 부르지 않는다.
      const clear = setTimeout(() => setSuggestions([]), 0);
      return () => clearTimeout(clear);
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetch(`/api/suggestions?q=${encodeURIComponent(keyword)}`, { signal: controller.signal })
        .then((response) => (response.ok ? response.json() : { items: [] }))
        .then((body: { items?: string[] }) => setSuggestions(body.items ?? []))
        .catch(() => setSuggestions([]));
    }, SUGGESTION_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [value, defaultValue]);

  return (
    <>
      <Label htmlFor="q" className="sr-only">
        검색어
      </Label>
      <Input
        id="q"
        name="q"
        type="search"
        // list가 있으면 역할이 combobox다.
        list={LIST_ID}
        autoComplete="off"
        maxLength={100}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder="상품 이름, 상호로 찾기"
      />
      <datalist id={LIST_ID}>
        {suggestions.map((name) => (
          <option key={name} value={name} />
        ))}
      </datalist>
    </>
  );
}
