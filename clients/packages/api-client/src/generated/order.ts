// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/order.json

export interface paths {
    "/v1/cart/items/{skuId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["changeQuantity"];
        post?: never;
        delete: operations["remove"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["orders"];
        put?: never;
        post: operations["place"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/orders/{orderId}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cart/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["add"];
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
    "/v1/seller/orders": {
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
    "/v1/seller/orders/{sellerOrderId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/orders/{orderId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["order"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/cart": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["cart"];
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
        ChangeCartItemQuantityRequest: {
            /** Format: int32 */
            quantity: number;
        };
        Line: {
            /** Format: uuid */
            skuId: string;
            /** Format: int32 */
            quantity: number;
            /** Format: int64 */
            expectedPrice: number;
        };
        PlaceOrderRequest: {
            lines: components["schemas"]["Line"][];
            shipping: components["schemas"]["Shipping"];
            issuedCouponIds?: string[];
            /** Format: int64 */
            expectedAmount?: number;
        };
        Shipping: {
            recipientName?: string;
            recipientPhone?: string;
            zipCode?: string;
            baseAddress?: string;
            detailAddress?: string;
        };
        PlaceOrderResponse: {
            /** Format: uuid */
            orderId?: string;
        };
        AddCartItemRequest: {
            /** Format: uuid */
            skuId: string;
            /** Format: int32 */
            quantity: number;
        };
        DiscardDeadLetterRequest: {
            reason?: string;
        };
        PageViewSellerOrderSummaryView: {
            items?: components["schemas"]["SellerOrderSummaryView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
        };
        SellerOrderClaimView: {
            /** @enum {string} */
            type?: "CANCEL" | "RETURN" | "EXCHANGE";
            /** @enum {string} */
            status?: "REQUESTED" | "APPROVED" | "REJECTED" | "MEDIATING" | "COMPLETED";
        };
        SellerOrderSummaryView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "PENDING" | "AWAITING_PAYMENT" | "PAID" | "CANCELLED";
            /** @enum {string} */
            progress?: "PAID" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CONFIRMED" | "CANCELLED" | "RETURNED";
            claim?: components["schemas"]["SellerOrderClaimView"];
            /** Format: int64 */
            amount?: number;
            firstProductName?: string;
            /** Format: int32 */
            lineCount?: number;
            /** Format: date-time */
            placedAt?: string;
            /** Format: date-time */
            paidAt?: string;
        };
        OrderLineView: {
            /** Format: uuid */
            orderLineId?: string;
            /** Format: uuid */
            skuId?: string;
            /** Format: uuid */
            productId?: string;
            productName?: string;
            optionNames?: string[];
            optionValues?: string[];
            /** Format: int64 */
            unitPrice?: number;
            /** Format: int32 */
            quantity?: number;
            /** Format: int64 */
            amount?: number;
        };
        OrderShippingView: {
            recipientName?: string;
            recipientPhone?: string;
            zipCode?: string;
            baseAddress?: string;
            detailAddress?: string;
        };
        SellerOrderDetailView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "PENDING" | "AWAITING_PAYMENT" | "PAID" | "CANCELLED";
            /** @enum {string} */
            progress?: "PAID" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CONFIRMED" | "CANCELLED" | "RETURNED";
            claim?: components["schemas"]["SellerOrderClaimView"];
            /** Format: int64 */
            amount?: number;
            shipping?: components["schemas"]["OrderShippingView"];
            lines?: components["schemas"]["OrderLineView"][];
            /** Format: date-time */
            placedAt?: string;
            /** Format: date-time */
            paidAt?: string;
        };
        CursorPageViewOrderSummaryView: {
            items?: components["schemas"]["OrderSummaryView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
        };
        OrderSummaryView: {
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "PENDING" | "AWAITING_PAYMENT" | "PAID" | "CANCELLED";
            /** @enum {string} */
            cancelReason?: "STOCK_UNAVAILABLE" | "COUPON_UNAVAILABLE" | "PAYMENT_TIMEOUT" | "BUYER_CANCELLED";
            /** Format: int64 */
            amount?: number;
            orderName?: string;
            /** Format: date-time */
            placedAt?: string;
        };
        OrderDetailView: {
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "PENDING" | "AWAITING_PAYMENT" | "PAID" | "CANCELLED";
            /** @enum {string} */
            cancelReason?: "STOCK_UNAVAILABLE" | "COUPON_UNAVAILABLE" | "PAYMENT_TIMEOUT" | "BUYER_CANCELLED";
            /** Format: int64 */
            amount?: number;
            orderName?: string;
            shipping?: components["schemas"]["OrderShippingView"];
            sellerOrders?: components["schemas"]["OrderSellerOrderView"][];
            /** Format: date-time */
            placedAt?: string;
            /** Format: date-time */
            paidAt?: string;
        };
        OrderSellerOrderView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            sellerId?: string;
            storeName?: string;
            /** Format: int64 */
            amount?: number;
            /** Format: int64 */
            sellerCouponDiscount?: number;
            /** Format: int64 */
            platformCouponDiscount?: number;
            /** Format: int64 */
            paymentAmount?: number;
            /** @enum {string} */
            progress?: "PAID" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CONFIRMED" | "CANCELLED" | "RETURNED";
            claim?: components["schemas"]["SellerOrderClaimView"];
            lines?: components["schemas"]["OrderLineView"][];
        };
        CartItemView: {
            /** Format: uuid */
            skuId?: string;
            /** Format: uuid */
            productId?: string;
            productName?: string;
            optionNames?: string[];
            optionValues?: string[];
            /** Format: int64 */
            price?: number;
            /** Format: int32 */
            quantity?: number;
            /** @enum {string} */
            status?: "AVAILABLE" | "INSUFFICIENT_STOCK" | "SOLD_OUT" | "NOT_ON_SALE";
            /** Format: uuid */
            thumbnailFileId?: string;
            /** Format: date-time */
            addedAt?: string;
        };
        CartSellerGroupView: {
            /** Format: uuid */
            sellerId?: string;
            storeName?: string;
            items?: components["schemas"]["CartItemView"][];
        };
        CartView: {
            sellers?: components["schemas"]["CartSellerGroupView"][];
            missingSkuIds?: string[];
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
    changeQuantity: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                skuId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeCartItemQuantityRequest"];
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
                skuId: string;
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
    orders: {
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
                    "application/json": components["schemas"]["CursorPageViewOrderSummaryView"];
                };
            };
        };
    };
    place: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PlaceOrderRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PlaceOrderResponse"];
                };
            };
        };
    };
    cancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                orderId: string;
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
    add: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddCartItemRequest"];
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
                progress?: ("PAID" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CONFIRMED" | "CANCELLED" | "RETURNED")[];
                claimStatus?: ("REQUESTED" | "APPROVED" | "REJECTED" | "MEDIATING" | "COMPLETED")[];
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
                    "application/json": components["schemas"]["PageViewSellerOrderSummaryView"];
                };
            };
        };
    };
    detail: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sellerOrderId: string;
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
                    "application/json": components["schemas"]["SellerOrderDetailView"];
                };
            };
        };
    };
    order: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                orderId: string;
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
                    "application/json": components["schemas"]["OrderDetailView"];
                };
            };
        };
    };
    cart: {
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
                    "application/json": components["schemas"]["CartView"];
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
