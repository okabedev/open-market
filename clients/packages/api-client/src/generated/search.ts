// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/search.json

export interface paths {
    "/v1/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["search"];
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
        ProductSearchItemView: {
            /** Format: uuid */
            productId?: string;
            name?: string;
            storeName?: string;
            /** Format: int64 */
            minPrice?: number;
            inStock?: boolean;
            /** Format: uuid */
            thumbnailFileId?: string;
            /** Format: date-time */
            publishedAt?: string;
            ratingAverage?: number;
            /** Format: int64 */
            ratingCount?: number;
        };
        ProductSearchResultView: {
            items?: components["schemas"]["ProductSearchItemView"][];
            /** Format: int64 */
            totalCount?: number;
            nextCursor?: string;
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
    search: {
        parameters: {
            query?: {
                q?: string;
                categoryId?: string;
                minPrice?: number;
                maxPrice?: number;
                minRating?: number;
                sort?: "RELEVANCE" | "LATEST" | "PRICE_ASC" | "PRICE_DESC" | "RATING" | "SALES";
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
                    "application/json": components["schemas"]["ProductSearchResultView"];
                };
            };
        };
    };
}
