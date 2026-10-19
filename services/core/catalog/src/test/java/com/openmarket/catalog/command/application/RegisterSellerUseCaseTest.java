package com.openmarket.catalog.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.catalog.command.domain.Seller;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegisterSellerUseCaseTest {

    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    final CatalogFakes.Sellers sellers = new CatalogFakes.Sellers();
    final RegisterSellerUseCase useCase =
            new RegisterSellerUseCase(sellers, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("입점 승인을 받으면 상호를 가진 판매 중 판매자 사본을 만들고, 다시 받아도 하나다")
    void registersSellerCopyOnce() {
        UUID sellerId = UUID.randomUUID();

        useCase.execute(new RegisterSellerCommand(sellerId, "길동상회"));
        useCase.execute(new RegisterSellerCommand(sellerId, "길동상회"));

        Seller seller = sellers.rows.get(sellerId);
        assertThat(sellers.rows).hasSize(1);
        assertThat(seller.storeName()).isEqualTo("길동상회");
        assertThat(seller.status().name()).isEqualTo("ACTIVE");
    }
}
