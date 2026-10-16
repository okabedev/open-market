package com.openmarket.common.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** common-web 테스트용 앱. 실제 서비스처럼 공통 빈은 자동설정으로만 등록되게, 스캔 범위를 테스트 컨트롤러 패키지로 한정한다. */
@SpringBootApplication(scanBasePackages = "com.openmarket.common.web.testapp")
public class TestApplication {}
