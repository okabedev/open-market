package com.openmarket.common.web.testapp;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.common.web.idempotency.Idempotent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 핸들러가 실제로 몇 번 실행됐는지 상품 이름별로 센다. */
@RestController
@RequestMapping("/test/idempotency")
public class IdempotencyTestController {

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    public int calls(String item) {
        AtomicInteger count = calls.get(item);
        return count == null ? 0 : count.get();
    }

    @Idempotent
    @PostMapping("/orders")
    public ResponseEntity<OrderResult> place(@RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrderResult(request.item(), increment(request)));
    }

    /** 첫 호출만 실패한다. */
    @Idempotent
    @PostMapping("/flaky")
    public OrderResult flaky(@RequestBody OrderRequest request) {
        int count = increment(request);
        if (count == 1) {
            throw new IllegalStateException("일시 장애");
        }
        return new OrderResult(request.item(), count);
    }

    @Idempotent
    @PostMapping("/rejected")
    public OrderResult rejected(@RequestBody OrderRequest request) {
        increment(request);
        throw new BusinessException(ErrorType.UNPROCESSABLE, "TEST_OUT_OF_STOCK", "재고가 없습니다.");
    }

    private int increment(OrderRequest request) {
        return calls.computeIfAbsent(request.item(), item -> new AtomicInteger()).incrementAndGet();
    }

    public record OrderRequest(String item) {}

    public record OrderResult(String item, int callCount) {}
}
