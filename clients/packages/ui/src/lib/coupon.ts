/** 쿠폰 할인 조건(promotion 쿠폰 정책, docs/architecture/promotion.md "쿠폰 정책"). */
export type CouponTerms = {
  discountType?: "FIXED" | "RATE";
  discountValue?: number;
  maxDiscount?: number;
  minOrderAmount?: number;
};

function won(value: number | undefined): string {
  return `${(value ?? 0).toLocaleString("ko-KR")}원`;
}

/** 할인과 조건 문구. 예: "10% 할인(최대 5,000원) · 30,000원 이상 구매 시". */
export function couponTerms(terms: CouponTerms): string {
  const discount =
    terms.discountType === "RATE"
      ? `${terms.discountValue}% 할인${terms.maxDiscount ? `(최대 ${won(terms.maxDiscount)})` : ""}`
      : `${won(terms.discountValue)} 할인`;
  return terms.minOrderAmount
    ? `${discount} · ${won(terms.minOrderAmount)} 이상 구매 시`
    : discount;
}

export type CouponPhase = "SCHEDULED" | "ISSUING" | "ENDED" | "STOPPED";

export const COUPON_PHASE_LABELS: Record<CouponPhase, string> = {
  SCHEDULED: "발급 예정",
  ISSUING: "발급 중",
  ENDED: "발급 끝남",
  STOPPED: "발급 중지",
};

/**
 * 판매자·관리자 목록의 발급 단계. promotion 상태는 `ISSUING`과 `STOPPED`뿐이고, 내려받을 수 있는지는 발급 기간으로 정한다(끝 시각부터
 * 못 받는다, promotion.md "쿠폰 정책").
 */
export function couponPhase(
  coupon: { status?: "ISSUING" | "STOPPED"; issueStartAt?: string; issueEndAt?: string },
  now: Date,
): CouponPhase {
  if (coupon.status === "STOPPED") {
    return "STOPPED";
  }
  if (coupon.issueStartAt && now < new Date(coupon.issueStartAt)) {
    return "SCHEDULED";
  }
  if (coupon.issueEndAt && now >= new Date(coupon.issueEndAt)) {
    return "ENDED";
  }
  return "ISSUING";
}

// 한국 시간은 서머타임이 없어 UTC+9로 고정이다.
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const LOCAL_INPUT = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

/** `<input type="datetime-local">` 값(분 단위)을 한국 시간으로 읽어 ISO 시각으로 바꾼다. 형식이 틀리거나 없는 날짜면 null이다. */
export function kstInputToIso(value: string): string | null {
  const match = LOCAL_INPUT.exec(value);
  if (!match) {
    return null;
  }
  const [year, month, day, hour, minute] = match.slice(1).map(Number) as [
    number,
    number,
    number,
    number,
    number,
  ];
  const utc = Date.UTC(year, month - 1, day, hour, minute);
  const check = new Date(utc);
  if (
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() !== month - 1 ||
    check.getUTCDate() !== day ||
    check.getUTCHours() !== hour ||
    check.getUTCMinutes() !== minute
  ) {
    return null;
  }
  return new Date(utc - KST_OFFSET_MS).toISOString();
}

/** 시각을 한국 시간의 `datetime-local` 값(분 단위, 초는 버린다)으로 쓴다. 폼의 기본값에 쓴다. */
export function toKstInput(date: Date): string {
  return new Date(date.getTime() + KST_OFFSET_MS).toISOString().slice(0, 16);
}
