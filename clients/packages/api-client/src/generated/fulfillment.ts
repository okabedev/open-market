// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/fulfillment.json

export interface paths {
    "/v1/seller/seller-orders/{sellerOrderId}/ship": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["ship"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/seller-orders/{sellerOrderId}/accept": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["accept"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/claims/{claimId}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/claims/{claimId}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["approve"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller-orders/{sellerOrderId}/confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller-orders/{sellerOrderId}/claims": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["requestClaim"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller-orders/{sellerOrderId}/cancel": {
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
    "/v1/claims/{claimId}/mediation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["requestMediation"];
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
    "/v1/admin/claims/{claimId}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reject_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/claims/{claimId}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["approve_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/seller-orders/{sellerOrderId}": {
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
    "/v1/seller-orders/{sellerOrderId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail_1"];
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
    "/v1/admin/claims": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["mediating"];
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
        ShipShipmentRequest: {
            carrier: string;
            trackingNumber: string;
        };
        RejectClaimRequest: {
            reason: string;
        };
        ApproveClaimRequest: {
            restock?: boolean;
        };
        RequestClaimRequest: {
            /** @enum {string} */
            type: "RETURN" | "EXCHANGE";
            /** @enum {string} */
            reason: "CHANGE_OF_MIND" | "DEFECTIVE" | "WRONG_ITEM" | "NOT_AS_DESCRIBED";
            description: string;
            fileIds?: string[];
        };
        RequestClaimResponse: {
            /** Format: uuid */
            claimId?: string;
        };
        DiscardDeadLetterRequest: {
            reason?: string;
        };
        RejectMediationRequest: {
            reason: string;
        };
        ApproveMediationRequest: {
            /** @enum {string} */
            fault: "BUYER" | "SELLER";
        };
        ClaimView: {
            /** Format: uuid */
            claimId?: string;
            /** @enum {string} */
            type?: "CANCEL" | "RETURN" | "EXCHANGE";
            /** @enum {string} */
            status?: "REQUESTED" | "APPROVED" | "REJECTED" | "MEDIATING" | "COMPLETED";
            /** @enum {string} */
            reason?: "CHANGE_OF_MIND" | "DEFECTIVE" | "WRONG_ITEM" | "NOT_AS_DESCRIBED" | "BUYER_CANCEL";
            /** @enum {string} */
            fault?: "BUYER" | "SELLER";
            description?: string;
            fileIds?: string[];
            restock?: boolean;
            /** Format: int64 */
            refundAmount?: number;
            refundFailureCode?: string;
            rejectionReason?: string;
            mediationReason?: string;
            /** Format: date-time */
            requestedAt?: string;
            /** Format: date-time */
            approvedAt?: string;
            /** Format: date-time */
            rejectedAt?: string;
            /** Format: date-time */
            mediationRequestedAt?: string;
            /** Format: date-time */
            completedAt?: string;
        };
        SellerFulfillmentView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "READY" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED";
            shipments?: components["schemas"]["ShipmentView"][];
            claims?: components["schemas"]["ClaimView"][];
        };
        ShipmentView: {
            /** Format: uuid */
            shipmentId?: string;
            /** @enum {string} */
            status?: "READY" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED";
            /** @enum {string} */
            carrier?: "CJ_LOGISTICS" | "HANJIN" | "LOTTE" | "LOGEN" | "KOREA_POST";
            trackingNumber?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            acceptedAt?: string;
            /** Format: date-time */
            shippedAt?: string;
            /** Format: date-time */
            inTransitAt?: string;
            /** Format: date-time */
            deliveredAt?: string;
            /** Format: date-time */
            cancelledAt?: string;
        };
        BuyerFulfillmentView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "READY" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED";
            shipments?: components["schemas"]["ShipmentView"][];
            /** Format: date-time */
            confirmDueAt?: string;
            /** Format: date-time */
            confirmedAt?: string;
            /** @enum {string} */
            confirmMode?: "BUYER" | "AUTO";
            claims?: components["schemas"]["ClaimView"][];
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
        MediationClaimView: {
            /** Format: uuid */
            orderId?: string;
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            sellerId?: string;
            /** Format: uuid */
            buyerId?: string;
            /** Format: int64 */
            amount?: number;
            /** Format: date-time */
            mediationRequestedAt?: string;
            claim?: components["schemas"]["ClaimView"];
        };
        PageViewMediationClaimView: {
            items?: components["schemas"]["MediationClaimView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
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
    ship: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sellerOrderId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ShipShipmentRequest"];
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
    accept: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    reject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                claimId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectClaimRequest"];
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
    approve: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                claimId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ApproveClaimRequest"];
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
    confirm: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    requestClaim: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sellerOrderId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RequestClaimRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RequestClaimResponse"];
                };
            };
        };
    };
    cancel: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    requestMediation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                claimId: string;
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
    reject_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                claimId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectMediationRequest"];
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
    approve_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                claimId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ApproveMediationRequest"];
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
                    "application/json": components["schemas"]["SellerFulfillmentView"];
                };
            };
        };
    };
    detail_1: {
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
                    "application/json": components["schemas"]["BuyerFulfillmentView"];
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
    mediating: {
        parameters: {
            query?: {
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
                    "application/json": components["schemas"]["PageViewMediationClaimView"];
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
