// 생성한 파일이다. 고치지 말고 `pnpm --filter @openmarket/api-client generate`로 다시 만든다.
// 원본: openapi/member.json

export interface paths {
    "/v1/members/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["me"];
        put: operations["changeProfile"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/members/me/addresses/{addressId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["changeAddress"];
        post?: never;
        delete: operations["removeAddress"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/members/me/addresses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["addresses"];
        put?: never;
        post: operations["addAddress"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/members/me/addresses/{addressId}/default": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["setDefaultAddress"];
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
        ChangeProfileRequest: {
            name: string;
            phone?: string;
        };
        ChangeAddressRequest: {
            label?: string;
            recipientName: string;
            recipientPhone: string;
            zipCode: string;
            baseAddress: string;
            detailAddress?: string;
        };
        AddAddressRequest: {
            label?: string;
            recipientName: string;
            recipientPhone: string;
            zipCode: string;
            baseAddress: string;
            detailAddress?: string;
            asDefault?: boolean;
        };
        AddAddressResponse: {
            /** Format: uuid */
            addressId?: string;
        };
        MemberView: {
            /** Format: uuid */
            memberId?: string;
            email?: string;
            name?: string;
            phone?: string;
            emailVerified?: boolean;
            /** Format: date-time */
            joinedAt?: string;
        };
        AddressView: {
            /** Format: uuid */
            addressId?: string;
            label?: string;
            recipientName?: string;
            recipientPhone?: string;
            zipCode?: string;
            baseAddress?: string;
            detailAddress?: string;
            isDefault?: boolean;
            /** Format: date-time */
            createdAt?: string;
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
    me: {
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
                    "application/json": components["schemas"]["MemberView"];
                };
            };
        };
    };
    changeProfile: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeProfileRequest"];
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
    changeAddress: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                addressId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeAddressRequest"];
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
    removeAddress: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                addressId: string;
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
    addresses: {
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
                    "application/json": components["schemas"]["AddressView"][];
                };
            };
        };
    };
    addAddress: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddAddressRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AddAddressResponse"];
                };
            };
        };
    };
    setDefaultAddress: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                addressId: string;
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
}
