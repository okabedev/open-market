// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/fulfillment.json

export interface paths {
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
        SellerFulfillmentShipmentView: {
            /** Format: uuid */
            shipmentId?: string;
            /** @enum {string} */
            status?: "READY" | "ACCEPTED" | "SHIPPED" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED";
            /** Format: date-time */
            createdAt?: string;
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
