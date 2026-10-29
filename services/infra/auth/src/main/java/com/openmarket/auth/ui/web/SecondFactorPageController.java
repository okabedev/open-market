package com.openmarket.auth.ui.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.openmarket.auth.command.application.ConfirmTotpEnrollmentCommand;
import com.openmarket.auth.command.application.ConfirmTotpEnrollmentUseCase;
import com.openmarket.auth.command.application.PrepareTotpEnrollmentCommand;
import com.openmarket.auth.command.application.PrepareTotpEnrollmentUseCase;
import com.openmarket.auth.command.application.SecondFactorSessionPort;
import com.openmarket.auth.command.application.SecurityEventPort;
import com.openmarket.auth.command.application.VerifySecondFactorCommand;
import com.openmarket.auth.command.application.VerifySecondFactorUseCase;
import com.openmarket.auth.command.application.VerifySecondFactorUseCase.Result;
import com.openmarket.auth.command.application.VerifySecondFactorUseCase.Status;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 2단계 인증 화면(docs/architecture/auth.md "관리자 2단계 인증"). 비밀번호를 확인한 관리자 세션만 연다. 인가 엔드포인트가 요소가 없는
 * 관리자를 여기로 보내고, 코드를 맞히면 저장한 인가 요청으로 돌아간다. 비밀 값, 코드, 복구 코드는 로그에 남기지 않는다.
 */
@Controller
public class SecondFactorPageController {

    private final PrepareTotpEnrollmentUseCase prepare;
    private final ConfirmTotpEnrollmentUseCase confirm;
    private final VerifySecondFactorUseCase verify;
    private final SecondFactorSessionPort session;
    private final SecurityEventPort securityEvents;

    public SecondFactorPageController(
            PrepareTotpEnrollmentUseCase prepare,
            ConfirmTotpEnrollmentUseCase confirm,
            VerifySecondFactorUseCase verify,
            SecondFactorSessionPort session,
            SecurityEventPort securityEvents) {
        this.prepare = prepare;
        this.confirm = confirm;
        this.verify = verify;
        this.session = session;
        this.securityEvents = securityEvents;
    }

    /** 등록하지 않았으면 등록 화면(QR, 비밀 값), 등록했으면 코드 입력 화면. */
    @GetMapping("/login/totp")
    public String page(
            Principal principal,
            @RequestParam(required = false) @Nullable String error,
            @RequestParam(required = false) @Nullable String limited,
            Model model) {
        String message = null;
        if (limited != null) {
            message = "시도가 너무 많습니다. 잠시 뒤 다시 시도해 주세요.";
        } else if (error != null) {
            message = "코드가 올바르지 않습니다. 5번 틀리면 15분 동안 로그인할 수 없습니다.";
        }
        model.addAttribute("message", message);
        Optional<PrepareTotpEnrollmentUseCase.Result> enrollment =
                prepare.execute(new PrepareTotpEnrollmentCommand(accountId(principal)));
        if (enrollment.isEmpty()) {
            return "totp";
        }
        model.addAttribute("secret", enrollment.get().secret());
        model.addAttribute("qrSvg", qrSvg(enrollment.get().otpauthUri()));
        return "totp-enroll";
    }

    /** 등록 확인. 맞으면 복구 코드를 한 번 보여 주고 계속할 주소를 준다. */
    @PostMapping("/login/totp/enroll")
    public String enroll(
            Principal principal,
            @RequestParam String code,
            HttpServletRequest request,
            Model model) {
        Result result =
                confirm.execute(new ConfirmTotpEnrollmentCommand(accountId(principal), code));
        if (result.status() != Status.VERIFIED) {
            return failure(result.status(), accountId(principal), request.getRemoteAddr());
        }
        model.addAttribute("recoveryCodes", result.recoveryCodes());
        model.addAttribute("continueUrl", session.complete());
        return "totp-recovery";
    }

    /** 코드(또는 복구 코드) 확인. 맞으면 저장한 인가 요청으로 돌아간다. */
    @PostMapping("/login/totp")
    public String verify(
            Principal principal, @RequestParam String code, HttpServletRequest request) {
        Result result = verify.execute(new VerifySecondFactorCommand(accountId(principal), code));
        if (result.status() != Status.VERIFIED) {
            return failure(result.status(), accountId(principal), request.getRemoteAddr());
        }
        return "redirect:" + session.complete();
    }

    /** 틀린 코드와 이번 실패로 인한 잠금은 보안 이벤트로 남긴다. */
    private String failure(Status status, UUID accountId, String ip) {
        if (status == Status.INVALID || status == Status.LOCKED_BY_FAILURE) {
            securityEvents.secondFactorFailed(accountId, ip);
        }
        if (status == Status.LOCKED_BY_FAILURE) {
            securityEvents.accountLocked(accountId, ip);
        }
        return switch (status) {
            case LOCKED, LOCKED_BY_FAILURE -> {
                session.end();
                yield "redirect:/login?locked";
            }
            case NOT_ENROLLED -> "redirect:/login/totp";
            default -> "redirect:/login/totp?error";
        };
    }

    private static UUID accountId(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    /** QR 둘레 빈칸(모듈 수). 인증 앱이 읽으려면 4칸이 필요하다. */
    private static final int QUIET_ZONE = 4;

    /** 등록 화면의 QR 코드를 SVG로 그린다(ZXing core). 외부 리소스나 이미지 파일 없이 화면에 바로 넣는다. */
    private static String qrSvg(String text) {
        BitMatrix matrix;
        try {
            matrix =
                    new QRCodeWriter()
                            .encode(
                                    text,
                                    BarcodeFormat.QR_CODE,
                                    0,
                                    0,
                                    Map.of(
                                            EncodeHintType.MARGIN,
                                            0,
                                            EncodeHintType.ERROR_CORRECTION,
                                            ErrorCorrectionLevel.M));
        } catch (WriterException e) {
            throw new IllegalStateException("QR 코드를 만들 수 없다", e);
        }
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        int size = matrix.getWidth() + QUIET_ZONE * 2;
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""
                + -QUIET_ZONE
                + " "
                + -QUIET_ZONE
                + " "
                + size
                + " "
                + size
                + "\" width=\"200\" height=\"200\" shape-rendering=\"crispEdges\" role=\"img\""
                + " aria-label=\"인증 앱에 등록할 QR 코드\"><rect x=\""
                + -QUIET_ZONE
                + "\" y=\""
                + -QUIET_ZONE
                + "\" width=\"100%\" height=\"100%\" fill=\"#fff\"/><path d=\""
                + path
                + "\" fill=\"#000\"/></svg>";
    }
}
