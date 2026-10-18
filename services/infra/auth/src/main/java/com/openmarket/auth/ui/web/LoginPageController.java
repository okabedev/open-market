package com.openmarket.auth.ui.web;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** auth가 그리는 유일한 화면인 로그인 화면(docs/architecture/auth.md). 폼 처리는 Spring Security가 한다. */
@Controller
public class LoginPageController {

    private final String signupUrl;

    public LoginPageController(@Value("${openmarket.auth.signup-url}") String signupUrl) {
        this.signupUrl = signupUrl;
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) @Nullable String error,
            @RequestParam(required = false) @Nullable String locked,
            @RequestParam(required = false) @Nullable String limited,
            Model model) {
        String message = null;
        if (locked != null) {
            message = "로그인을 5번 잘못해서 15분 동안 로그인할 수 없습니다. 잠시 뒤 다시 시도해 주세요.";
        } else if (limited != null) {
            message = "로그인 시도가 너무 많습니다. 잠시 뒤 다시 시도해 주세요.";
        } else if (error != null) {
            message = "이메일 또는 비밀번호가 올바르지 않습니다.";
        }
        model.addAttribute("message", message);
        model.addAttribute("signupUrl", signupUrl);
        return "login";
    }
}
