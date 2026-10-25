// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/settlement.json

export interface paths {
    "/v1/admin/commission-rates/{categoryId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["set"];
        post?: never;
        delete: operations["remove"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/settlements/{settlementId}/payout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["payOut"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/settlement-runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["run"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/settlements": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["settlements"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/settlements/{settlementId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["settlement"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/settlements/{settlementId}/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["settlementItems"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/settlement-items/pending": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["pendingItems"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/settlement-items/pending/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["pendingSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/settlements": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/settlements/{settlementId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["settlement_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/settlements/{settlementId}/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["items"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/commission-rates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_1"];
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
        SetCommissionRateRequest: {
            /** Format: int32 */
            rateBp: number;
        };
        RunSettlementResponse: {
            /** Format: int32 */
            confirmedCount?: number;
        };
        CursorPageViewSettlementView: {
            items?: components["schemas"]["SettlementView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        SettlementView: {
            /** Format: uuid */
            settlementId?: string;
            /** Format: uuid */
            sellerId?: string;
            /** Format: date-time */
            periodStart?: string;
            /** Format: date-time */
            periodEnd?: string;
            /** Format: int32 */
            itemCount?: number;
            /** Format: int64 */
            salesAmount?: number;
            /** Format: int64 */
            commissionAmount?: number;
            /** Format: int64 */
            sellerCouponDiscount?: number;
            /** Format: int64 */
            amount?: number;
            /** @enum {string} */
            status?: "CONFIRMED" | "PAID_OUT";
            /** Format: date-time */
            confirmedAt?: string;
            /** Format: date-time */
            paidOutAt?: string;
        };
        CursorPageViewSettlementItemView: {
            items?: components["schemas"]["SettlementItemView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        SettlementItemLineView: {
            /** Format: uuid */
            orderLineId?: string;
            /** Format: uuid */
            productId?: string;
            productName?: string;
            /** Format: uuid */
            categoryId?: string;
            /** Format: int32 */
            quantity?: number;
            /** Format: int64 */
            amount?: number;
            /** Format: int32 */
            commissionRateBp?: number;
            /** Format: int64 */
            commissionAmount?: number;
        };
        SettlementItemView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            orderId?: string;
            /** Format: date-time */
            confirmedAt?: string;
            /** Format: date-time */
            periodStart?: string;
            /** Format: date-time */
            periodEnd?: string;
            /** Format: int64 */
            salesAmount?: number;
            /** Format: int64 */
            commissionAmount?: number;
            /** Format: int64 */
            sellerCouponDiscount?: number;
            /** Format: int64 */
            settlementAmount?: number;
            lines?: components["schemas"]["SettlementItemLineView"][];
        };
        PendingSettlementSummaryView: {
            /** Format: int64 */
            itemCount?: number;
            /** Format: int64 */
            salesAmount?: number;
            /** Format: int64 */
            commissionAmount?: number;
            /** Format: int64 */
            sellerCouponDiscount?: number;
            /** Format: int64 */
            settlementAmount?: number;
        };
        PageViewSettlementView: {
            items?: components["schemas"]["SettlementView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
        };
        CommissionRateView: {
            /** Format: uuid */
            categoryId?: string;
            /** Format: int32 */
            rateBp?: number;
            /** Format: date-time */
            updatedAt?: string;
        };
        CommissionRatesView: {
            /** Format: int32 */
            defaultRateBp?: number;
            rates?: components["schemas"]["CommissionRateView"][];
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
    set: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                categoryId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetCommissionRateRequest"];
            };
        };
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
    remove: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                categoryId: string;
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
    payOut: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                settlementId: string;
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
    run: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["RunSettlementResponse"];
                };
            };
        };
    };
    settlements: {
        parameters: {
            query?: {
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
                    "application/json": components["schemas"]["CursorPageViewSettlementView"];
                };
            };
        };
    };
    settlement: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                settlementId: string;
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
                    "application/json": components["schemas"]["SettlementView"];
                };
            };
        };
    };
    settlementItems: {
        parameters: {
            query?: {
                cursor?: string;
                size?: number;
            };
            header?: never;
            path: {
                settlementId: string;
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
                    "application/json": components["schemas"]["CursorPageViewSettlementItemView"];
                };
            };
        };
    };
    pendingItems: {
        parameters: {
            query?: {
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
                    "application/json": components["schemas"]["CursorPageViewSettlementItemView"];
                };
            };
        };
    };
    pendingSummary: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["PendingSettlementSummaryView"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: {
                status?: "CONFIRMED" | "PAID_OUT";
                sellerId?: string;
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
                    "application/json": components["schemas"]["PageViewSettlementView"];
                };
            };
        };
    };
    settlement_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                settlementId: string;
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
                    "application/json": components["schemas"]["SettlementView"];
                };
            };
        };
    };
    items: {
        parameters: {
            query?: {
                cursor?: string;
                size?: number;
            };
            header?: never;
            path: {
                settlementId: string;
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
                    "application/json": components["schemas"]["CursorPageViewSettlementItemView"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: never;
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
                    "application/json": components["schemas"]["CommissionRatesView"];
                };
            };
        };
    };
}
