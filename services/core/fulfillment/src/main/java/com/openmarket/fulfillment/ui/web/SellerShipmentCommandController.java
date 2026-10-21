package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.AcceptShipmentCommand;
import com.openmarket.fulfillment.command.application.AcceptShipmentUseCase;
import com.openmarket.fulfillment.command.application.ShipShipmentCommand;
import com.openmarket.fulfillment.command.application.ShipShipmentUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 하위 주문의 발주 확인과 발송(FUL-1). 주체는 토큰의 {@code sub}(= sellerId)다. 상태로 결과가 정해져 다시 보내도 같으므로 {@code
 * Idempotency-Key}는 받지 않는다.
 */
@RestController
@RequestMapping("/v1/seller/seller-orders/{sellerOrderId}")
public class SellerShipmentCommandController {

    private final AcceptShipmentUseCase accept;
    private final ShipShipmentUseCase ship;

    public SellerShipmentCommandController(AcceptShipmentUseCase accept, ShipShipmentUseCase ship) {
        this.accept = accept;
        this.ship = ship;
    }

    /** 발주 확인. 이미 확인했으면 그대로 204, 발송 뒤면 409 {@code FULFILLMENT_INVALID_STATE}. */
    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(Principal principal, @PathVariable UUID sellerOrderId) {
        accept.execute(new AcceptShipmentCommand(sellerId(principal), sellerOrderId));
    }

    /** 발송. 같은 송장으로 이미 발송했으면 그대로 204, 발주 확인 전이나 다른 송장이면 409. */
    @PostMapping("/ship")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(
            Principal principal,
            @PathVariable UUID sellerOrderId,
            @Valid @RequestBody ShipShipmentRequest request) {
        ship.execute(
                new ShipShipmentCommand(
                        sellerId(principal),
                        sellerOrderId,
                        request.carrier(),
                        request.trackingNumber()));
    }

    private static UUID sellerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
