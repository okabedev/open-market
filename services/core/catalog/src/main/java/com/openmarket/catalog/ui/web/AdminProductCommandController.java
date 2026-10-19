package com.openmarket.catalog.ui.web;

import com.openmarket.catalog.command.application.BanProductCommand;
import com.openmarket.catalog.command.application.BanProductUseCase;
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

/** 관리자 상품 제재(CAT-5, ADM-2). 주체는 토큰의 {@code sub}(= 관리자 ID)이고 감사 로그에 남는다. */
@RestController
@RequestMapping("/v1/admin/products")
public class AdminProductCommandController {

    private final BanProductUseCase ban;

    public AdminProductCommandController(BanProductUseCase ban) {
        this.ban = ban;
    }

    /**
     * 상품을 제재한다. 상태와 상관없이 판매가 막히고 판매자는 풀 수 없다. 이미 제재했으면 204(사유는 바뀌지 않는다). 사유가 없으면 400 {@code
     * CATALOG_INVALID_INPUT}.
     */
    @PostMapping("/{productId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(
            Principal principal,
            @PathVariable UUID productId,
            @Valid @RequestBody BanProductRequest request) {
        ban.execute(
                new BanProductCommand(
                        UUID.fromString(principal.getName()), productId, request.reason()));
    }
}
