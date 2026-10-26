import { kstInputToIso, type FormState } from "@openmarket/ui";

/** 쿠폰 정책 규칙의 상한(docs/architecture/promotion.md "쿠폰 정책"). 최종 검사는 promotion이 한다(400 `PROMOTION_INVALID_INPUT`). */
export const COUPON_LIMITS = {
  name: 50,
  rate: 100,
  quantity: 1_000_000,
  validDays: 365,
} as const;

export type CouponFormData = {
  name: string;
  discountType: "FIXED" | "RATE";
  discountValue: number;
  maxDiscount?: number;
  minOrderAmount: number;
  quantity: number;
  issueStartAt: string;
  issueEndAt: string;
  validDays: number;
};

const FIELDS = [
  "name",
  "discountType",
  "discountValue",
  "maxDiscount",
  "minOrderAmount",
  "quantity",
  "issueStartAt",
  "issueEndAt",
  "validDays",
] as const;

/** 폼에 다시 채울 입력값. 서비스가 거절했을 때도 쓴다. */
export function couponFormValues(form: FormData): Record<(typeof FIELDS)[number], string> {
  return Object.fromEntries(FIELDS.map((name) => [name, String(form.get(name) ?? "")])) as Record<
    (typeof FIELDS)[number],
    string
  >;
}

/** 정수 칸. 비었거나 정수가 아니면 null이다. */
function integer(value: string): number | null {
  const trimmed = value.trim();
  return /^\d+$/.test(trimmed) ? Number(trimmed) : null;
}

function within(value: number | null, min: number, max: number): value is number {
  return value !== null && value >= min && value <= max;
}

/**
 * 쿠폰 만들기 폼을 읽고 검사한다(PRM-1). 발급 시각은 한국 시간 `datetime-local` 값을 ISO로 바꾼다. 할인 값의 범위는 할인 방식에 따라
 * 다르고(정액 1원 이상, 정률 1~100%), 최대 할인은 정률일 때만 보낸다. 발급 끝은 시작과 지금보다 뒤여야 한다(시작은 지나도 된다). 칸끼리
 * 비교하는 규칙이 있어 칸마다 직접 검사하고 오류를 한꺼번에 돌려준다.
 */
export function readCouponForm(
  form: FormData,
  now: Date,
): { success: true; data: CouponFormData } | { success: false; state: FormState } {
  const values = couponFormValues(form);
  const errors: Record<string, string> = {};

  const name = values.name.trim();
  if (name.length < 1 || name.length > COUPON_LIMITS.name) {
    errors.name = `쿠폰 이름을 1~${COUPON_LIMITS.name}자로 써 주세요.`;
  }

  const discountType =
    values.discountType === "FIXED" || values.discountType === "RATE" ? values.discountType : null;
  if (!discountType) {
    errors.discountType = "할인 방식을 골라 주세요.";
  }

  const discountValue = integer(values.discountValue);
  if (discountType === "RATE" && !within(discountValue, 1, COUPON_LIMITS.rate)) {
    errors.discountValue = `할인율은 1~${COUPON_LIMITS.rate}%입니다.`;
  } else if (discountType !== "RATE" && !within(discountValue, 1, Number.MAX_SAFE_INTEGER)) {
    errors.discountValue = "할인 금액은 1원 이상입니다.";
  }

  const maxDiscount = values.maxDiscount.trim() === "" ? undefined : integer(values.maxDiscount);
  if (
    discountType === "RATE" &&
    maxDiscount !== undefined &&
    !within(maxDiscount, 1, Number.MAX_SAFE_INTEGER)
  ) {
    errors.maxDiscount = "최대 할인은 1원 이상입니다. 상한이 없으면 비워 두세요.";
  }

  const minOrderAmount = values.minOrderAmount.trim() === "" ? 0 : integer(values.minOrderAmount);
  if (minOrderAmount === null) {
    errors.minOrderAmount = "최소 주문 금액은 0원 이상입니다.";
  }

  const quantity = integer(values.quantity);
  if (!within(quantity, 1, COUPON_LIMITS.quantity)) {
    errors.quantity = `발급 수량은 1~${COUPON_LIMITS.quantity.toLocaleString("ko-KR")}장입니다.`;
  }

  const issueStartAt = kstInputToIso(values.issueStartAt);
  if (!issueStartAt) {
    errors.issueStartAt = "발급 시작 시각을 골라 주세요.";
  }
  const issueEndAt = kstInputToIso(values.issueEndAt);
  if (!issueEndAt) {
    errors.issueEndAt = "발급 끝 시각을 골라 주세요.";
  } else if (issueStartAt && issueEndAt <= issueStartAt) {
    errors.issueEndAt = "발급 끝은 시작보다 뒤여야 합니다.";
  } else if (new Date(issueEndAt) <= now) {
    errors.issueEndAt = "발급 끝은 지금보다 뒤여야 합니다.";
  }

  const validDays = integer(values.validDays);
  if (!within(validDays, 1, COUPON_LIMITS.validDays)) {
    errors.validDays = `사용 기한은 발급 후 1~${COUPON_LIMITS.validDays}일입니다.`;
  }

  if (
    Object.keys(errors).length > 0 ||
    !discountType ||
    discountValue === null ||
    minOrderAmount === null ||
    quantity === null ||
    !issueStartAt ||
    !issueEndAt ||
    validDays === null
  ) {
    return {
      success: false,
      state: { status: "error", message: "입력을 확인해 주세요.", fieldErrors: errors, values },
    };
  }
  return {
    success: true,
    data: {
      name,
      discountType,
      discountValue,
      ...(discountType === "RATE" && typeof maxDiscount === "number" ? { maxDiscount } : {}),
      minOrderAmount,
      quantity,
      issueStartAt,
      issueEndAt,
      validDays,
    },
  };
}
