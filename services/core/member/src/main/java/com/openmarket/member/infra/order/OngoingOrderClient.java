package com.openmarket.member.infra.order;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * order 내부 API(docs/architecture/order.md "내부 API"). Eureka로 찾고, 서비스 간 토큰과 서킷 브레이커가 붙는다(config-repo
 * member.yml). {@code openmarket.member.order-url}이 있으면 그 주소로 바로 부른다(테스트).
 */
@FeignClient(name = "order", contextId = "ongoingOrder", url = "${openmarket.member.order-url:}")
interface OngoingOrderClient {

    @GetMapping("/internal/v1/buyers/{buyerId}/ongoing-orders")
    OngoingOrdersResponse ongoingOrders(@PathVariable("buyerId") UUID buyerId);

    record OngoingOrdersResponse(boolean hasOngoingOrders) {}
}
