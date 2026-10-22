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
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ShipShipmentRequest: {
            carrier: string;
            trackingNumber: string;
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
            /** Format: int64 */
            refundAmount?: number;
            refundFailureCode?: string;
            /** Format: date-time */
            requestedAt?: string;
            /** Format: date-time */
            approvedAt?: string;
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
}
