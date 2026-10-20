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
        get?: never;
        put?: never;
        post: operations["place"];
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
        OrderDetailView: {
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "PENDING" | "AWAITING_PAYMENT" | "PAID" | "CANCELLED";
            /** @enum {string} */
            cancelReason?: "STOCK_UNAVAILABLE" | "PAYMENT_TIMEOUT" | "BUYER_CANCELLED";
            /** Format: int64 */
            amount?: number;
            orderName?: string;
            shipping?: components["schemas"]["OrderShippingView"];
            sellerOrders?: components["schemas"]["OrderSellerOrderView"][];
            /** Format: date-time */
            placedAt?: string;
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
        OrderSellerOrderView: {
            /** Format: uuid */
            sellerOrderId?: string;
            /** Format: uuid */
            sellerId?: string;
            storeName?: string;
            /** Format: int64 */
            amount?: number;
            lines?: components["schemas"]["OrderLineView"][];
        };
        OrderShippingView: {
            recipientName?: string;
            recipientPhone?: string;
            zipCode?: string;
            baseAddress?: string;
            detailAddress?: string;
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
}
