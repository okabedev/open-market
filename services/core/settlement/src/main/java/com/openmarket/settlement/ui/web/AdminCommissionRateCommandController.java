package com.openmarket.settlement.ui.web;

import com.openmarket.settlement.command.application.RemoveCommissionRateCommand;
import com.openmarket.settlement.command.application.RemoveCommissionRateUseCase;
import com.openmarket.settlement.command.application.SetCommissionRateUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 수수료율 관리(docs/architecture/settlement.md "수수료율"). 주체는 토큰의 {@code sub}(= 관리자 ID)이고 바꾸면 감사 로그에
 * 남는다. 바꾼 요율은 그 뒤에 생기는 정산 대상부터 쓴다.
 */
@RestController
@RequestMapping("/v1/admin/commission-rates")
public class AdminCommissionRateCommandController {

    private final SetCommissionRateUseCase set;
    private final RemoveCommissionRateUseCase remove;

    public AdminCommissionRateCommandController(
            SetCommissionRateUseCase set, RemoveCommissionRateUseCase remove) {
        this.set = set;
        this.remove = remove;
    }

    /** 카테고리의 요율을 정한다. 같은 요율이면 204이고 아무것도 바뀌지 않는다. 범위 밖이면 400 {@code SETTLEMENT_INVALID_INPUT}. */
    @PutMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(
            Principal principal,
            @PathVariable UUID categoryId,
            @Valid @RequestBody SetCommissionRateRequest request) {
        set.execute(request.toCommand(UUID.fromString(principal.getName()), categoryId));
    }

    /** 카테고리의 요율을 지워 기본 요율로 돌린다. 요율이 없으면 204이고 아무것도 바뀌지 않는다. */
    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal principal, @PathVariable UUID categoryId) {
        remove.execute(
                new RemoveCommissionRateCommand(UUID.fromString(principal.getName()), categoryId));
    }
}
