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
        AddCartItemRequest: {
            /** Format: uuid */
            skuId: string;
            /** Format: int32 */
            quantity: number;
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
