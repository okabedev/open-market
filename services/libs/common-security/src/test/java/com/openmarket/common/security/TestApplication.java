package com.openmarket.common.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** common-security 테스트용 앱. 보안 설정은 자동설정으로만 등록되게, 스캔 범위를 테스트 컨트롤러 패키지로 한정한다. */
@SpringBootApplication(scanBasePackages = "com.openmarket.common.security.testapp")
public class TestApplication {}
