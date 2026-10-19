package com.openmarket.file.infra;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * file 실행 설정. 값은 infra/config-repo/file.yml에 둔다. 정리 주기({@code cleanup-interval})는 {@link
 * TempFileCleanupJob}이 직접 읽는다.
 *
 * @param storageRoot 파일을 저장하는 디렉터리. 컨테이너는 {@code /data}다
 */
@ConfigurationProperties("openmarket.file")
public record FileProperties(Path storageRoot) {}
