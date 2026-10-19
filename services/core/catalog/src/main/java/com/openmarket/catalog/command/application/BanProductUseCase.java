package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductBanned;
import com.openmarket.catalog.command.domain.ProductNotFoundException;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 상품을 제재한다(CAT-5, ADM-2). {@code ProductBanned}와 감사 로그(NFR-8)를 남긴다. 이미 제재했으면 아무것도 하지 않는다. */
@Service
public class BanProductUseCase {

    private final ProductRepository products;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public BanProductUseCase(
            ProductRepository products, Outbox outbox, AuditLog auditLog, Clock clock) {
        this.products = products;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(BanProductCommand command) {
        Product product =
                products.findForUpdateById(command.productId())
                        .orElseThrow(ProductNotFoundException::new);
        if (!product.ban(command.reason(), clock.instant())) {
            return;
        }
        products.save(product);
        String reason = Objects.requireNonNull(product.banReason());
        outbox.append(
                RegisterProductUseCase.TOPIC,
                product.id().toString(),
                ProductBanned.TYPE,
                new ProductBanned(
                        product.id(), product.nextRevision(), product.sellerId(), reason));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "PRODUCT_BANNED",
                        "PRODUCT",
                        product.id().toString(),
                        reason));
    }
}
