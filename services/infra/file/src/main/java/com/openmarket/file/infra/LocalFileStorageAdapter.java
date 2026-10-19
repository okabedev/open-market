package com.openmarket.file.infra;

import com.openmarket.file.command.application.FileStoragePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 로컬 볼륨 저장소(ADR 0008). 컨테이너는 {@code /data}(볼륨 {@code file-data}), IDE는 {@code
 * infra/compose/data/file}이다.
 *
 * <p>같은 디렉터리의 임시 파일에 다 쓴 뒤 이름을 바꿔서, 읽는 쪽이 반쯤 쓴 파일을 보지 않게 한다. 키가 루트 밖을 가리키면 거부한다.
 */
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path root;

    public LocalFileStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void write(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, content);
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("저장소 루트 밖의 키: " + key);
        }
        return path;
    }
}
