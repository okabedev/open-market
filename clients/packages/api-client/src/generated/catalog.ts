// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/catalog.json

export interface paths {
    "/v1/seller/products/{productId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail"];
        put: operations["update"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post: operations["register"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/products/{productId}/stop": {
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
    "/v1/seller/products/{productId}/resume": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["resume"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/seller/products/{productId}/publish": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["publish"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/products/{productId}/ban": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["ban"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/products/{productId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["product"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/categories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["categories"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/admin/products": {
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
        Sku: {
            /** Format: uuid */
            skuId?: string;
            optionValues: string[];
            /** Format: int64 */
            price?: number;
            /** Format: int32 */
            initialStock?: number;
        };
        UpdateProductRequest: {
            /** Format: uuid */
            categoryId: string;
            name: string;
            description: string;
            imageFileIds: string[];
            skus: components["schemas"]["Sku"][];
        };
        RegisterProductRequest: {
            /** Format: uuid */
            categoryId: string;
            name: string;
            description: string;
            imageFileIds: string[];
            optionNames?: string[];
            skus: components["schemas"]["Sku"][];
        };
        RegisterProductResponse: {
            /** Format: uuid */
            productId?: string;
        };
        BanProductRequest: {
            reason: string;
        };
        PageViewSellerProductSummaryView: {
            items?: components["schemas"]["SellerProductSummaryView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
        };
        SellerProductSummaryView: {
            /** Format: uuid */
            productId?: string;
            name?: string;
            /** @enum {string} */
            status?: "DRAFT" | "ON_SALE" | "STOPPED" | "BANNED";
            /** Format: uuid */
            thumbnailFileId?: string;
            /** Format: int64 */
            minPrice?: number;
            /** Format: int32 */
            skuCount?: number;
            /** Format: int64 */
            available?: number;
            /** Format: date-time */
            updatedAt?: string;
        };
        CategoryRefView: {
            /** Format: uuid */
            categoryId?: string;
            name?: string;
        };
        SellerProductView: {
            /** Format: uuid */
            productId?: string;
            /** @enum {string} */
            status?: "DRAFT" | "ON_SALE" | "STOPPED" | "BANNED";
            /** @enum {string} */
            stopReason?: "SELLER" | "SELLER_SUSPENDED";
            banReason?: string;
            name?: string;
            description?: string;
            categoryPath?: components["schemas"]["CategoryRefView"][];
            imageFileIds?: string[];
            optionNames?: string[];
            skus?: components["schemas"]["Sku"][];
            /** Format: date-time */
            publishedAt?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        ProductDetailView: {
            /** Format: uuid */
            productId?: string;
            /** @enum {string} */
            status?: "DRAFT" | "ON_SALE" | "STOPPED" | "BANNED";
            name?: string;
            description?: string;
            categoryPath?: components["schemas"]["CategoryRefView"][];
            /** Format: uuid */
            sellerId?: string;
            storeName?: string;
            imageFileIds?: string[];
            optionNames?: string[];
            skus?: components["schemas"]["Sku"][];
            /** Format: int64 */
            minPrice?: number;
            inStock?: boolean;
            /** Format: date-time */
            publishedAt?: string;
        };
        CategoryView: {
            /** Format: uuid */
            categoryId?: string;
            name?: string;
            children?: components["schemas"]["CategoryView"][];
        };
        AdminProductSummaryView: {
            /** Format: uuid */
            productId?: string;
            name?: string;
            /** @enum {string} */
            status?: "DRAFT" | "ON_SALE" | "STOPPED" | "BANNED";
            /** Format: uuid */
            sellerId?: string;
            storeName?: string;
            /** Format: uuid */
            thumbnailFileId?: string;
            /** Format: int64 */
            minPrice?: number;
            banReason?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        PageViewAdminProductSummaryView: {
            items?: components["schemas"]["AdminProductSummaryView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
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
                    "application/json": components["schemas"]["SellerProductView"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateProductRequest"];
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
    list: {
        parameters: {
            query?: {
                status?: "DRAFT" | "ON_SALE" | "STOPPED" | "BANNED";
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
                    "application/json": components["schemas"]["PageViewSellerProductSummaryView"];
                };
            };
        };
    };
    register: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegisterProductRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegisterProductResponse"];
                };
            };
        };
    };
    stop: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    resume: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    publish: {
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
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    ban: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BanProductRequest"];
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
    product: {
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
                    "application/json": components["schemas"]["ProductDetailView"];
                };
            };
        };
    };
    categories: {
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
                    "application/json": components["schemas"]["CategoryView"][];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                status?: "DRAFT" | "ON_SALE" | "STOPPED" | "BANNED";
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
                    "application/json": components["schemas"]["PageViewAdminProductSummaryView"];
                };
            };
        };
    };
}
