package com.openmarket.settlement.ui.web;

import com.openmarket.settlement.command.application.PayOutSettlementCommand;
import com.openmarket.settlement.command.application.PayOutSettlementUseCase;
import com.openmarket.settlement.command.application.RunSettlementUseCase;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 정산 작업 즉시 실행과 지급 처리(STL-3, STL-4, ADM-3). 주체는 토큰의 {@code sub}(= 관리자 ID)다. */
@RestController
@RequestMapping("/v1/admin")
public class AdminSettlementCommandController {

    private final RunSettlementUseCase run;
    private final PayOutSettlementUseCase payOut;

    public AdminSettlementCommandController(
            RunSettlementUseCase run, PayOutSettlementUseCase payOut) {
        this.run = run;
        this.payOut = payOut;
    }

    /** 정산 작업을 바로 한 번 돌린다. 정기 작업과 같은 규칙(마감 지연이 지난 기간)이고, 다시 돌려도 이미 확정한 판매자·기간은 넘겨 결과가 같다. */
    @PostMapping("/settlement-runs")
    public RunSettlementResponse run() {
        return new RunSettlementResponse(run.execute());
    }

    /** 확정된 정산을 지급 완료로 기록한다. 이미 지급했으면 204이고 아무것도 바뀌지 않는다. 없으면 404 {@code SETTLEMENT_NOT_FOUND}. */
    @PostMapping("/settlements/{settlementId}/payout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void payOut(Principal principal, @PathVariable UUID settlementId) {
        payOut.execute(
                new PayOutSettlementCommand(UUID.fromString(principal.getName()), settlementId));
    }
}
