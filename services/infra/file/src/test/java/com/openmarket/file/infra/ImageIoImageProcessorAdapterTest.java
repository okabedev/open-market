package com.openmarket.file.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.file.command.application.ImageProcessorPort.Rendition;
import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.ImageVariant;
import com.openmarket.file.command.domain.InvalidImageException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 픽스처는 src/test/resources/images/make_images.py가 만든다. */
class ImageIoImageProcessorAdapterTest {

    static final List<ImageVariant> VARIANTS = List.of(ImageVariant.W300, ImageVariant.W800);

    final ImageIoImageProcessorAdapter images = new ImageIoImageProcessorAdapter();

    @Test
    @DisplayName("JPEG의 크기를 읽고 폭 300, 800 JPEG 변형을 비율대로 만든다")
    void rendersJpegVariants() {
        byte[] content = fixture("landscape.jpg");

        assertThat(images.probe(content, ImageFormat.JPEG))
                .isEqualTo(new ImageDimensions(2400, 1600));
        List<Rendition> renditions = images.render(content, ImageFormat.JPEG, VARIANTS);

        assertThat(renditions)
                .extracting(Rendition::dimensions)
                .containsExactly(new ImageDimensions(300, 200), new ImageDimensions(800, 533));
        BufferedImage w800 = decode(renditions.get(1).jpeg());
        assertThat(w800.getWidth()).isEqualTo(800);
        assertThat(w800.getHeight()).isEqualTo(533);
    }

    @Test
    @DisplayName("EXIF 방향을 반영해 바로 세우고, 변형에는 EXIF(GPS 등)를 남기지 않는다")
    void appliesExifOrientationAndStripsMetadata() {
        byte[] content = fixture("exif-rotate-90.jpg");

        assertThat(images.probe(content, ImageFormat.JPEG)).isEqualTo(new ImageDimensions(80, 120));
        Rendition w300 = images.render(content, ImageFormat.JPEG, VARIANTS).getFirst();

        assertThat(w300.dimensions()).isEqualTo(new ImageDimensions(80, 120));
        BufferedImage image = decode(w300.jpeg());
        assertThat(isRed(image.getRGB(40, 10))).as("위쪽은 빨강").isTrue();
        assertThat(isBlue(image.getRGB(40, 110))).as("아래쪽은 파랑").isTrue();
        assertThat(containsExif(w300.jpeg())).isFalse();
    }

    @Test
    @DisplayName("CMYK JPEG도 RGB로 바꿔 만든다")
    void rendersCmykJpeg() {
        byte[] content = fixture("cmyk.jpg");

        Rendition w300 = images.render(content, ImageFormat.JPEG, VARIANTS).getFirst();

        assertThat(w300.dimensions()).isEqualTo(new ImageDimensions(200, 100));
        assertThat(isRed(decode(w300.jpeg()).getRGB(100, 50))).isTrue();
    }

    @Test
    @DisplayName("투명한 PNG는 흰 배경 위에 그린다")
    void flattensTransparencyOnWhite() {
        Rendition w300 = images.render(fixture("alpha.png"), ImageFormat.PNG, VARIANTS).getFirst();

        BufferedImage image = decode(w300.jpeg());
        assertThat(new Color(image.getRGB(5, 5)).getRed()).isGreaterThan(240);
        assertThat(new Color(image.getRGB(5, 5)).getGreen()).isGreaterThan(240);
        assertThat(isRed(image.getRGB(100, 100))).isTrue();
    }

    @Test
    @DisplayName("WebP를 읽는다")
    void rendersWebp() {
        byte[] content = fixture("sample.webp");

        assertThat(images.probe(content, ImageFormat.WEBP))
                .isEqualTo(new ImageDimensions(400, 300));
        Rendition w300 = images.render(content, ImageFormat.WEBP, VARIANTS).getFirst();
        assertThat(w300.dimensions()).isEqualTo(new ImageDimensions(300, 225));
    }

    @Test
    @DisplayName("1억 화소 이미지도 픽셀을 풀지 않고 크기만 읽는다")
    void probesHugeImageWithoutDecoding() {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();

        ImageDimensions dimensions = images.probe(fixture("huge-dimensions.png"), ImageFormat.PNG);

        long after = runtime.totalMemory() - runtime.freeMemory();
        assertThat(dimensions).isEqualTo(new ImageDimensions(10_000, 10_000));
        assertThat(after - before).isLessThan(64L * 1024 * 1024);
    }

    @Test
    @DisplayName("이미지가 아니거나 잘린 내용은 InvalidImageException이다")
    void rejectsCorruptContent() {
        assertThatThrownBy(() -> images.probe(fixture("not-image.jpg"), ImageFormat.JPEG))
                .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(
                        () -> images.render(fixture("truncated.jpg"), ImageFormat.JPEG, VARIANTS))
                .isInstanceOf(InvalidImageException.class);
    }

    static byte[] fixture(String name) {
        try (InputStream in =
                ImageIoImageProcessorAdapterTest.class.getResourceAsStream("/images/" + name)) {
            return Objects.requireNonNull(in, name).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BufferedImage decode(byte[] jpeg) {
        try {
            return Objects.requireNonNull(ImageIO.read(new ByteArrayInputStream(jpeg)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** JPEG APP1(0xFFE1) 세그먼트에 "Exif" 식별자가 있는가. */
    static boolean containsExif(byte[] jpeg) {
        for (int i = 0; i + 7 < jpeg.length; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF
                    && (jpeg[i + 1] & 0xFF) == 0xE1
                    && jpeg[i + 4] == 'E'
                    && jpeg[i + 5] == 'x'
                    && jpeg[i + 6] == 'i'
                    && jpeg[i + 7] == 'f') {
                return true;
            }
        }
        return false;
    }

    static boolean isRed(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() > 200 && color.getGreen() < 80 && color.getBlue() < 80;
    }

    static boolean isBlue(int rgb) {
        Color color = new Color(rgb);
        return color.getBlue() > 200 && color.getRed() < 80 && color.getGreen() < 80;
    }
}
