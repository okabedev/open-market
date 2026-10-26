import type { CouponTerms } from "@openmarket/ui";

/** 주문서에서 고를 수 있는 내 쿠폰(promotion `GET /v1/issued-coupons?usable=true`의 항목). */
export type UsableCoupon = CouponTerms & {
  issuedCouponId?: string;
  issuer?: "PLATFORM" | "SELLER";
  sellerId?: string;
  name?: string;
};

export type IssuedCouponState = "USABLE" | "RESERVED" | "USED" | "EXPIRED";

const STATE_LABELS: Record<IssuedCouponState, string> = {
  USABLE: "사용 가능",
  RESERVED: "주문에 사용 중",
  USED: "사용함",
  EXPIRED: "기간 만료",
};

/** 내 쿠폰 상태의 화면 이름. */
export function stateLabel(state: IssuedCouponState): string {
  return STATE_LABELS[state];
}

/** 주문서 줄을 판매자별 상품 금액으로 묶는다. 할인 미리보기의 하위 주문이다(판매자가 처음 나온 순서). */
export function sellerOrderAmounts(
  lines: readonly { sellerId: string; amount: number }[],
): { sellerId: string; amount: number }[] {
  const amounts = new Map<string, number>();
  for (const line of lines) {
    amounts.set(line.sellerId, (amounts.get(line.sellerId) ?? 0) + line.amount);
  }
  return [...amounts].map(([sellerId, amount]) => ({ sellerId, amount }));
}

export type CouponChoices = {
  platform: UsableCoupon[];
  /** 주문에 있는 판매자마다 그 판매자의 쿠폰. 쿠폰이 없는 판매자는 빈 목록이다 */
  bySeller: Map<string, UsableCoupon[]>;
};

/** 주문서에서 고를 수 있는 쿠폰. 플랫폼 쿠폰과, 주문에 상품이 있는 판매자의 쿠폰이다. */
export function couponChoices(
  usable: readonly UsableCoupon[],
  sellerIds: readonly string[],
): CouponChoices {
  const bySeller = new Map<string, UsableCoupon[]>(sellerIds.map((id) => [id, []]));
  const platform: UsableCoupon[] = [];
  for (const coupon of usable) {
    if (coupon.issuer === "PLATFORM") {
      platform.push(coupon);
    } else if (coupon.sellerId) {
      bySeller.get(coupon.sellerId)?.push(coupon);
    }
  }
  return { platform, bySeller };
}

function values(value: string | string[] | undefined): string[] {
  return Array.isArray(value) ? value : value ? [value] : [];
}

/**
 * 주소(`?platformCoupon=&sellerCoupon=`)에서 고른 발급 쿠폰. 고를 수 있는 쿠폰만, 플랫폼 한 장과 판매자마다 한 장을 플랫폼, 판매자 순으로
 * 쓴다. 플랫폼 쿠폰을 둘 주면 쓰지 않는다(화면이 하나만 보낸다).
 */
export function selectedCouponIds(
  params: { platformCoupon?: string | string[]; sellerCoupon?: string | string[] },
  choices: CouponChoices,
): string[] {
  const ids: string[] = [];
  const platform = values(params.platformCoupon);
  if (
    platform.length === 1 &&
    choices.platform.some((coupon) => coupon.issuedCouponId === platform[0])
  ) {
    ids.push(platform[0]!);
  }
  const usedSellers = new Set<string>();
  for (const id of values(params.sellerCoupon)) {
    for (const [sellerId, coupons] of choices.bySeller) {
      if (!usedSellers.has(sellerId) && coupons.some((coupon) => coupon.issuedCouponId === id)) {
        usedSellers.add(sellerId);
        ids.push(id);
      }
    }
  }
  return ids;
}

/** 할인 미리보기(`POST /v1/coupon-discounts`) 결과를 주문서 금액 칸으로 줄인다. */
export function discountSummary(result: {
  sellerOrders?: readonly { sellerId?: string; sellerCouponDiscount?: number }[];
  platformCouponDiscount?: number;
  paymentAmount?: number;
}): { sellerDiscount: number; platformDiscount: number; paymentAmount: number } {
  return {
    sellerDiscount: (result.sellerOrders ?? []).reduce(
      (sum, order) => sum + (order.sellerCouponDiscount ?? 0),
      0,
    ),
    platformDiscount: result.platformCouponDiscount ?? 0,
    paymentAmount: result.paymentAmount ?? 0,
  };
}

const UNAVAILABLE: Record<string, string> = {
  NOT_OWNED: "내 쿠폰이 아닙니다.",
  NOT_AVAILABLE: "다른 주문에 사용 중이거나 이미 사용했습니다.",
  EXPIRED: "사용 기간이 지났습니다.",
  MIN_ORDER_AMOUNT_NOT_MET: "최소 주문 금액이 모자랍니다.",
  SELLER_NOT_IN_ORDER: "주문에 그 판매자의 상품이 없습니다.",
  DUPLICATE_SLOT: "같은 자리에 쿠폰을 두 장 쓸 수 없습니다.",
};

/** 쓸 수 없는 쿠폰의 이유(promotion "할인 계산") 문구. */
export function unavailableMessage(reason: string | undefined): string {
  return UNAVAILABLE[reason ?? ""] ?? "쓸 수 없는 쿠폰입니다.";
}

type Notice = { variant: "success" | "destructive"; text: string };

const NOTICES: Record<string, Notice> = {
  "coupon-issued": {
    variant: "success",
    text: "쿠폰을 받았습니다. 내 쿠폰에서 확인할 수 있습니다.",
  },
  "coupon-sold-out": { variant: "destructive", text: "쿠폰이 모두 소진됐습니다." },
  "coupon-not-issuing": { variant: "destructive", text: "지금은 받을 수 없는 쿠폰입니다." },
  "coupon-busy": {
    variant: "destructive",
    text: "요청이 많아 받지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
  },
  "coupon-failed": {
    variant: "destructive",
    text: "쿠폰을 받지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
  },
};

/** 쿠폰 받기 결과(`?notice=`) 안내. 모르는 값이면 null. */
export function issueNotice(notice: string | string[] | undefined): Notice | null {
  return typeof notice === "string" ? (NOTICES[notice] ?? null) : null;
}
