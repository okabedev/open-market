// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/payment.json

export interface paths {
    "/v1/payments/confirm": {
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
    "/v1/payments/orders/{orderId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["payment"];
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
        ConfirmPaymentRequest: {
            /** Format: uuid */
            orderId: string;
            paymentKey: string;
            /** Format: int64 */
            amount: number;
        };
        ConfirmPaymentResponse: {
            /** Format: uuid */
            paymentId?: string;
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "APPROVED" | "IN_PROGRESS";
            /** Format: int64 */
            amount?: number;
            method?: string;
        };
        PaymentView: {
            /** Format: uuid */
            orderId?: string;
            /** @enum {string} */
            status?: "READY" | "IN_PROGRESS" | "APPROVED" | "PARTIALLY_CANCELLED" | "FAILED" | "CANCELLED";
            /** Format: int64 */
            amount?: number;
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
    confirm: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfirmPaymentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ConfirmPaymentResponse"];
                };
            };
        };
    };
    payment: {
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
                    "application/json": components["schemas"]["PaymentView"];
                };
            };
        };
    };
}
