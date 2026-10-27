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
    "/v1/coupon-discounts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["preview"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/dead-letters/{id}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["retryDeadLetter"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/dead-letters/{id}/discard": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["discardDeadLetter"];
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
    "/v1/issued-coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["mine"];
        put?: never;
        post?: never;
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
    "/v1/admin/dead-letters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listDeadLetters"];
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
        PreviewCouponDiscountRequest: {
            issuedCouponIds: string[];
            sellerOrders: components["schemas"]["SellerOrder"][];
        };
        SellerOrder: {
            /** Format: uuid */
            sellerId: string;
            /** Format: int64 */
            amount?: number;
        };
        Result: {
            applicable?: boolean;
            sellerOrders?: components["schemas"]["SellerOrder"][];
            /** Format: uuid */
            platformIssuedCouponId?: string;
            /** Format: int64 */
            platformCouponDiscount?: number;
            /** Format: int64 */
            paymentAmount?: number;
            unavailable?: components["schemas"]["Unavailable"][];
        };
        Unavailable: {
            /** Format: uuid */
            issuedCouponId?: string;
            reason?: string;
        };
        DiscardDeadLetterRequest: {
            reason?: string;
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
        CursorPageViewIssuedCouponView: {
            items?: components["schemas"]["IssuedCouponView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        IssuedCouponView: {
            /** Format: uuid */
            issuedCouponId?: string;
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
            /** @enum {string} */
            state?: "USABLE" | "RESERVED" | "USED" | "EXPIRED";
            /** Format: date-time */
            issuedAt?: string;
            /** Format: date-time */
            expiresAt?: string;
        };
        IssuableCouponsView: {
            items?: components["schemas"]["CouponView"][];
        };
        DeadLetterPage: {
            items?: components["schemas"]["DeadLetterView"][];
            nextCursor?: string;
        };
        DeadLetterView: {
            /** Format: uuid */
            id?: string;
            consumerGroup?: string;
            /** Format: uuid */
            eventId?: string;
            eventType?: string;
            originalTopic?: string;
            /** Format: int32 */
            originalPartition?: number;
            /** Format: int64 */
            originalOffset?: number;
            exceptionClass?: string;
            traceId?: string;
            status?: string;
            /** Format: date-time */
            deadLetteredAt?: string;
            /** Format: date-time */
            resolvedAt?: string;
            resolvedBy?: string;
            discardReason?: string;
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
    preview: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PreviewCouponDiscountRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Result"];
                };
            };
        };
    };
    retryDeadLetter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
                content?: never;
            };
        };
    };
    discardDeadLetter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DiscardDeadLetterRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
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
    mine: {
        parameters: {
            query?: {
                usable?: boolean;
                cursor?: string;
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
                    "application/json": components["schemas"]["CursorPageViewIssuedCouponView"];
                };
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
    listDeadLetters: {
        parameters: {
            query?: {
                status?: string;
                topic?: string;
                before?: string;
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
                    "application/json": components["schemas"]["DeadLetterPage"];
                };
            };
        };
    };
}
