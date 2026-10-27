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
    "/v1/seller/products/{productId}/skus/{skuId}/stock-adjustments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["stockAdjustments"];
        put?: never;
        post: operations["adjustStock"];
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
        UpdateProductRequest: {
            /** Format: uuid */
            categoryId: string;
            name: string;
            description: string;
            imageFileIds: string[];
            skus: components["schemas"]["UpdateProductSkuRequest"][];
        };
        UpdateProductSkuRequest: {
            /** Format: uuid */
            skuId?: string;
            optionValues: string[];
            /** Format: int64 */
            price?: number;
            /** Format: int32 */
            initialStock?: number;
        };
        RegisterProductRequest: {
            /** Format: uuid */
            categoryId: string;
            name: string;
            description: string;
            imageFileIds: string[];
            optionNames?: string[];
            skus: components["schemas"]["RegisterProductSkuRequest"][];
        };
        RegisterProductSkuRequest: {
            optionValues: string[];
            /** Format: int64 */
            price?: number;
            /** Format: int32 */
            initialStock?: number;
        };
        RegisterProductResponse: {
            /** Format: uuid */
            productId?: string;
        };
        AdjustStockRequest: {
            /** Format: int32 */
            delta: number;
            reason?: string;
        };
        AdjustStockResponse: {
            /** Format: uuid */
            adjustmentId?: string;
            /** Format: int32 */
            onHand?: number;
            /** Format: int32 */
            reserved?: number;
            /** Format: int32 */
            available?: number;
        };
        BanProductRequest: {
            reason: string;
        };
        DiscardDeadLetterRequest: {
            reason?: string;
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
        SellerProductSkuView: {
            /** Format: uuid */
            skuId?: string;
            optionValues?: string[];
            /** Format: int64 */
            price?: number;
            /** Format: int32 */
            onHand?: number;
            /** Format: int32 */
            reserved?: number;
            /** Format: int32 */
            available?: number;
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
            skus?: components["schemas"]["SellerProductSkuView"][];
            /** Format: date-time */
            publishedAt?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        PageViewStockAdjustmentView: {
            items?: components["schemas"]["StockAdjustmentView"][];
            /** Format: int64 */
            totalCount?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
        };
        StockAdjustmentView: {
            /** Format: uuid */
            adjustmentId?: string;
            /** Format: int32 */
            delta?: number;
            /** Format: int32 */
            onHandAfter?: number;
            reason?: string;
            /** Format: date-time */
            adjustedAt?: string;
        };
        ProductDetailSkuView: {
            /** Format: uuid */
            skuId?: string;
            optionValues?: string[];
            /** Format: int64 */
            price?: number;
            inStock?: boolean;
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
            skus?: components["schemas"]["ProductDetailSkuView"][];
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
    stockAdjustments: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
            };
            header?: never;
            path: {
                productId: string;
                skuId: string;
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
                    "application/json": components["schemas"]["PageViewStockAdjustmentView"];
                };
            };
        };
    };
    adjustStock: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productId: string;
                skuId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdjustStockRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdjustStockResponse"];
                };
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
