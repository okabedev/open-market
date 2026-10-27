// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/review.json

export interface paths {
    "/v1/seller/reviews/{reviewId}/reply": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["reply"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["myReviews"];
        put?: never;
        post: operations["write"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/reviews/{reviewId}/show": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["show"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/reviews/{reviewId}/hide": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["hide"];
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
    "/v1/seller/reviews": {
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
    "/v1/reviews/writable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["writable"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/products/{productId}/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["productReviews"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/products/{productId}/rating-summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ratingSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/reviews": {
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
    "/v1/admin/audit-logs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listAuditLogs"];
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
        WriteReviewReplyRequest: {
            content: string;
        };
        WriteReviewRequest: {
            /** Format: uuid */
            orderLineId: string;
            /** Format: int32 */
            rating?: number;
            content: string;
            fileIds?: string[];
        };
        WriteReviewResponse: {
            /** Format: uuid */
            reviewId?: string;
        };
        ChangeReviewVisibilityRequest: {
            reason: string;
        };
        DiscardDeadLetterRequest: {
            reason?: string;
        };
        CursorPageViewReviewView: {
            items?: components["schemas"]["ReviewView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        ReviewReplyView: {
            content?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        ReviewView: {
            /** Format: uuid */
            reviewId?: string;
            /** Format: uuid */
            orderLineId?: string;
            /** Format: uuid */
            productId?: string;
            productName?: string;
            optionValues?: string[];
            /** Format: uuid */
            sellerId?: string;
            /** Format: uuid */
            buyerId?: string;
            /** Format: int32 */
            rating?: number;
            content?: string;
            fileIds?: string[];
            /** @enum {string} */
            status?: "VISIBLE" | "HIDDEN";
            reply?: components["schemas"]["ReviewReplyView"];
            /** Format: date-time */
            createdAt?: string;
        };
        CursorPageViewWritableReviewView: {
            items?: components["schemas"]["WritableReviewView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        WritableReviewView: {
            /** Format: uuid */
            orderLineId?: string;
            /** Format: uuid */
            orderId?: string;
            /** Format: uuid */
            productId?: string;
            productName?: string;
            optionValues?: string[];
            /** Format: date-time */
            confirmedAt?: string;
        };
        CursorPageViewProductReviewView: {
            items?: components["schemas"]["ProductReviewView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        ProductReviewView: {
            /** Format: uuid */
            reviewId?: string;
            optionValues?: string[];
            /** Format: int32 */
            rating?: number;
            content?: string;
            fileIds?: string[];
            reply?: components["schemas"]["ReviewReplyView"];
            /** Format: date-time */
            createdAt?: string;
        };
        RatingSummaryView: {
            /** Format: uuid */
            productId?: string;
            /** Format: int64 */
            ratingCount?: number;
            ratingAverage?: number;
        };
        PageViewReviewView: {
            items?: components["schemas"]["ReviewView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
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
        AuditLogPage: {
            items?: components["schemas"]["AuditLogView"][];
            nextCursor?: string;
        };
        AuditLogView: {
            /** Format: uuid */
            id?: string;
            actorId?: string;
            action?: string;
            targetType?: string;
            targetId?: string;
            detail?: string;
            traceId?: string;
            /** Format: date-time */
            occurredAt?: string;
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
    reply: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WriteReviewReplyRequest"];
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
    myReviews: {
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
                    "application/json": components["schemas"]["CursorPageViewReviewView"];
                };
            };
        };
    };
    write: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WriteReviewRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WriteReviewResponse"];
                };
            };
        };
    };
    show: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeReviewVisibilityRequest"];
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
    hide: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reviewId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeReviewVisibilityRequest"];
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
    list: {
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
                    "application/json": components["schemas"]["CursorPageViewReviewView"];
                };
            };
        };
    };
    writable: {
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
                    "application/json": components["schemas"]["CursorPageViewWritableReviewView"];
                };
            };
        };
    };
    productReviews: {
        parameters: {
            query?: {
                cursor?: string;
                size?: number;
            };
            header?: never;
            path: {
                productId: string;
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
                    "application/json": components["schemas"]["CursorPageViewProductReviewView"];
                };
            };
        };
    };
    ratingSummary: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productId: string;
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
                    "application/json": components["schemas"]["RatingSummaryView"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                status?: "VISIBLE" | "HIDDEN";
                productId?: string;
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
                    "application/json": components["schemas"]["PageViewReviewView"];
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
    listAuditLogs: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
                actorId?: string;
                action?: string;
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
                    "application/json": components["schemas"]["AuditLogPage"];
                };
            };
        };
    };
}
