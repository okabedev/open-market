package com.openmarket.fulfillment.ui.web;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.fulfillment.command.application.ConfirmPurchaseCommand;
import com.openmarket.fulfillment.command.application.ConfirmPurchaseUseCase;
import com.openmarket.fulfillment.command.application.RequestCancelClaimCommand;
import com.openmarket.fulfillment.command.application.RequestCancelClaimUseCase;
import com.openmarket.fulfillment.command.application.RequestClaimCommand;
import com.openmarket.fulfillment.command.application.RequestClaimUseCase;
import com.openmarket.fulfillment.query.application.FulfillmentQueryService;
import com.openmarket.fulfillment.query.dto.BuyerFulfillmentView;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 하위 주문의 배송 조회, 구매확정, 발송 전 취소, 반품·교환 신청(FUL-2, FUL-3, CLM-1, CLM-2). 주체는 토큰의 {@code sub}(=
 * buyerId)다. 구매확정과 취소는 다시 보내도 결과가 같아 {@code Idempotency-Key}를 받지 않는다.
 */
@RestController
@RequestMapping("/v1/seller-orders/{sellerOrderId}")
public class BuyerFulfillmentController {

    private final FulfillmentQueryService fulfillments;
    private final ConfirmPurchaseUseCase confirm;
    private final RequestCancelClaimUseCase cancel;
    private final RequestClaimUseCase requestClaim;

    public BuyerFulfillmentController(
            FulfillmentQueryService fulfillments,
            ConfirmPurchaseUseCase confirm,
            RequestCancelClaimUseCase cancel,
            RequestClaimUseCase requestClaim) {
        this.fulfillments = fulfillments;
        this.confirm = confirm;
        this.cancel = cancel;
        this.requestClaim = requestClaim;
    }

    /** 본인 하위 주문의 배송. 남의 것이거나 배송이 아직 없으면 404 {@code FULFILLMENT_NOT_FOUND}. */
    @GetMapping
    public BuyerFulfillmentView detail(Principal principal, @PathVariable UUID sellerOrderId) {
        return fulfillments.buyerFulfillment(buyerId(principal), sellerOrderId);
    }

    /** 구매확정. 이미 확정했으면 그대로 204, 배송 완료 전이면 409 {@code FULFILLMENT_INVALID_STATE}. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(Principal principal, @PathVariable UUID sellerOrderId) {
        confirm.execute(new ConfirmPurchaseCommand(buyerId(principal), sellerOrderId));
    }

    /**
     * 발송 전 취소(CLM-1). 이미 취소했으면 그대로 204, 발송 뒤면 409 {@code FULFILLMENT_INVALID_STATE}, 진행 중인 클레임이 있으면
     * 409 {@code FULFILLMENT_CLAIM_IN_PROGRESS}.
     */
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Principal principal, @PathVariable UUID sellerOrderId) {
        cancel.execute(new RequestCancelClaimCommand(buyerId(principal), sellerOrderId));
    }

    /**
     * 반품·교환 신청(CLM-2). {@code Idempotency-Key}가 필요하다. 배송 완료 전이면 409 {@code
     * FULFILLMENT_INVALID_STATE}, 구매확정했으면 409 {@code FULFILLMENT_PURCHASE_CONFIRMED}, 진행 중인 클레임이
     * 있으면 409 {@code FULFILLMENT_CLAIM_IN_PROGRESS}, 규칙을 어기면 400 {@code FULFILLMENT_INVALID_INPUT}.
     */
    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public RequestClaimResponse requestClaim(
            Principal principal,
            @PathVariable UUID sellerOrderId,
            @Valid @RequestBody RequestClaimRequest request) {
        return new RequestClaimResponse(
                requestClaim.execute(
                        new RequestClaimCommand(
                                buyerId(principal),
                                sellerOrderId,
                                request.type(),
                                request.reason(),
                                request.description(),
                                Objects.requireNonNullElse(request.fileIds(), List.of()))));
    }

    private static UUID buyerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
