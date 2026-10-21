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
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ShipShipmentRequest: {
            carrier: string;
            trackingNumber: string;
        };
        SellerFulfillmentShipmentView: {
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
        };
        SellerFulfillmentView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "READY" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED";
            shipments?: components["schemas"]["SellerFulfillmentShipmentView"][];
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
}
