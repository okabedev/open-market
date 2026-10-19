package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.SellerRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 사본을 만든다. 이미 있으면 아무것도 하지 않는다(같은 승인 이벤트를 다시 받아도 결과가 같다). */
@Service
public class RegisterSellerUseCase {

    private final SellerRepository sellers;
    private final Clock clock;

    public RegisterSellerUseCase(SellerRepository sellers, Clock clock) {
        this.sellers = sellers;
        this.clock = clock;
    }

    @Transactional
    public void execute(RegisterSellerCommand command) {
        if (sellers.findById(command.sellerId()).isPresent()) {
            return;
        }
        sellers.save(Seller.approve(command.sellerId(), command.storeName(), clock.instant()));
    }
}
