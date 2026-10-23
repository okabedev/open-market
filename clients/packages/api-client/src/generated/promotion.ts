// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/promotion.json

export interface paths {
    "/v1/seller/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/coupons/{couponId}/stop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["stop"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/coupons/{couponId}/issue": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["issue"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_1"];
        put?: never;
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/coupons/{couponId}/stop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["stop_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["issuable"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/coupons/{couponId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["coupon"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        CreateCouponRequest: {
            name: string;
            /** @enum {string} */
            discountType: "FIXED" | "RATE";
            /** Format: int64 */
            discountValue?: number;
            /** Format: int64 */
            maxDiscount?: number;
            /** Format: int64 */
            minOrderAmount?: number;
            /** Format: int32 */
            quantity?: number;
            /** Format: date-time */
            issueStartAt: string;
            /** Format: date-time */
            issueEndAt: string;
            /** Format: int32 */
            validDays?: number;
        };
        CreateCouponResponse: {
            /** Format: uuid */
            couponId?: string;
        };
        IssueCouponResponse: {
            /** Format: uuid */
            issuedCouponId?: string;
        };
        CouponView: {
            /** Format: uuid */
            couponId?: string;
            /** @enum {string} */
            issuer?: "PLATFORM" | "SELLER";
            /** Format: uuid */
            sellerId?: string;
            name?: string;
            /** @enum {string} */
            discountType?: "FIXED" | "RATE";
            /** Format: int64 */
            discountValue?: number;
            /** Format: int64 */
            maxDiscount?: number;
            /** Format: int64 */
            minOrderAmount?: number;
            /** Format: int32 */
            quantity?: number;
            /** Format: date-time */
            issueStartAt?: string;
            /** Format: date-time */
            issueEndAt?: string;
            /** Format: int32 */
            validDays?: number;
            /** @enum {string} */
            status?: "ISSUING" | "STOPPED";
            /** Format: date-time */
            stoppedAt?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: int32 */
            issuedCount?: number;
        };
        PageViewCouponView: {
            items?: components["schemas"]["CouponView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
        };
        IssuableCouponsView: {
            items?: components["schemas"]["CouponView"][];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    list: {
        parameters: {
            query?: {
                status?: "ISSUING" | "STOPPED";
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageViewCouponView"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateCouponRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateCouponResponse"];
                };
            };
        };
    };
    stop: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                couponId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    issue: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                couponId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IssueCouponResponse"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                issuer?: "PLATFORM" | "SELLER";
                sellerId?: string;
                status?: "ISSUING" | "STOPPED";
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageViewCouponView"];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateCouponRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateCouponResponse"];
                };
            };
        };
    };
    stop_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                couponId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    issuable: {
        parameters: {
            query?: {
                sellerId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IssuableCouponsView"];
                };
            };
        };
    };
    coupon: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                couponId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CouponView"];
                };
            };
        };
    };
}
