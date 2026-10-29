package com.openmarket.file.infra;

import com.openmarket.file.command.application.ImageProcessorPort;
import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.ImageVariant;
import com.openmarket.file.command.domain.InvalidImageException;
import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * JDK ImageIO + TwelveMonkeys 리더로 이미지를 읽고 JPEG 변형을 만든다(ADR 0017).
 *
 * <ul>
 *   <li>크기는 머리만 읽는다. 픽셀은 변형을 만들 때만 푼다
 *   <li>큰 이미지는 서브샘플링으로 가장 큰 변형 폭의 2배 이상만 남기고, 푸는 픽셀을 {@link #MAX_DECODED_PIXELS} 이하로 묶는다. 힙을 원본
 *       해상도와 상관없이 일정하게 쓴다
 *   <li>동시에 변형을 만드는 요청은 {@link #MAX_CONCURRENT_RENDERS}개로 막는다
 *   <li>JPEG의 EXIF 방향을 반영한다. WebP·PNG의 방향 정보는 읽지 않는다
 *   <li>투명한 부분은 흰색으로 채운다. 결과에는 메타데이터(EXIF, ICC)를 넣지 않는다
 * </ul>
 */
public class ImageIoImageProcessorAdapter implements ImageProcessorPort {

    static final int MAX_CONCURRENT_RENDERS = 2;

    /** 한 번에 푸는 픽셀 수 상한. 푼 이미지와 흰 배경 사본을 합쳐 요청 하나가 약 56MB를 쓴다. */
    static final long MAX_DECODED_PIXELS = 8_000_000L;

    private static final float JPEG_QUALITY = 0.85f;
    private static final int NORMAL = 1;

    private final Semaphore renders = new Semaphore(MAX_CONCURRENT_RENDERS);

    public ImageIoImageProcessorAdapter() {
        // Boot 실행 jar 안의 TwelveMonkeys 플러그인을 등록한다. ImageIO는 처음 쓸 때의 클래스로더로만 찾는다.
        ImageIO.scanForPlugins();
        // 디스크 캐시 파일을 만들지 않는다. 내용은 이미 메모리에 있다.
        ImageIO.setUseCache(false);
    }

    @Override
    public ImageDimensions probe(byte[] content, ImageFormat format) {
        try (ImageInputStream in = input(content)) {
            ImageReader reader = reader(format, in);
            try {
                return upright(
                        new ImageDimensions(reader.getWidth(0), reader.getHeight(0)),
                        orientation(content, format));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException(e);
        }
    }

    @Override
    public List<Rendition> render(byte[] content, ImageFormat format, List<ImageVariant> variants) {
        renders.acquireUninterruptibly();
        try {
            return renderAll(content, format, variants);
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException(e);
        } finally {
            renders.release();
        }
    }

    private List<Rendition> renderAll(
            byte[] content, ImageFormat format, List<ImageVariant> variants) throws IOException {
        int orientation = orientation(content, format);
        BufferedImage decoded;
        ImageDimensions source;
        try (ImageInputStream in = input(content)) {
            ImageReader reader = reader(format, in);
            try {
                source =
                        upright(
                                new ImageDimensions(reader.getWidth(0), reader.getHeight(0)),
                                orientation);
                int largest =
                        variants.stream()
                                .mapToInt(variant -> variant.targetWidth(source.width()))
                                .max()
                                .orElse(source.width());
                int step = subsampling(source, largest);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                decoded = reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
        BufferedImage flat = flattenOnWhite(decoded);
        return variants.stream()
                .map(variant -> renderOne(flat, source, orientation, variant))
                .toList();
    }

    /**
     * 픽셀을 몇 개마다 하나씩 읽을지. 가장 큰 변형 폭의 2배 이상을 남기되, 푸는 픽셀이 {@link #MAX_DECODED_PIXELS}를 넘지 않게 한다. 폭만 보면
     * 좁고 긴 이미지(예: 1599x31000)를 원본 해상도로 푼다.
     */
    static int subsampling(ImageDimensions source, int largestWidth) {
        int byWidth = source.width() / (2 * largestWidth);
        int byPixels = (int) Math.ceil(Math.sqrt((double) source.pixels() / MAX_DECODED_PIXELS));
        return Math.max(1, Math.max(byWidth, byPixels));
    }

    private static Rendition renderOne(
            BufferedImage flat, ImageDimensions source, int orientation, ImageVariant variant) {
        int width = variant.targetWidth(source.width());
        int height = Math.max(1, Math.round((float) source.height() * width / source.width()));
        // 방향을 바꾸기 전 이미지 기준으로 줄인 뒤 바로 세운다. 작은 이미지에서 돌리는 편이 싸다.
        BufferedImage resized =
                swapsAxes(orientation) ? resize(flat, height, width) : resize(flat, width, height);
        BufferedImage upright = orient(resized, orientation);
        return new Rendition(variant, encodeJpeg(upright), new ImageDimensions(width, height));
    }

    private static ImageInputStream input(byte[] content) throws IOException {
        ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content));
        if (in == null) {
            throw new IOException("이미지 입력 스트림을 만들 수 없다");
        }
        return in;
    }

    private static ImageReader reader(ImageFormat format, ImageInputStream in) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(format.contentType());
        if (!readers.hasNext()) {
            throw new IOException("리더가 없다: " + format);
        }
        ImageReader reader = readers.next();
        reader.setInput(in, true, true);
        return reader;
    }

    /** JPEG EXIF의 방향 값(1~8). 없거나 읽지 못하면 1(그대로)이다. APP1 "Exif" 세그먼트의 TIFF 구조에서 읽는다. */
    static int orientation(byte[] content, ImageFormat format) {
        if (format != ImageFormat.JPEG) {
            return NORMAL;
        }
        try (ImageInputStream in = input(content)) {
            List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(in, JPEG.APP1, "Exif");
            if (segments.isEmpty()) {
                return NORMAL;
            }
            try (InputStream data = segments.getFirst().data()) {
                // 식별자 "Exif\0" 뒤의 채움 바이트 하나를 건너뛴다.
                data.read();
                Directory exif = new TIFFReader().read(ImageIO.createImageInputStream(data));
                Entry entry = exif.getEntryById(TIFF.TAG_ORIENTATION);
                if (entry == null || !(entry.getValue() instanceof Number value)) {
                    return NORMAL;
                }
                int orientation = value.intValue();
                return orientation >= 1 && orientation <= 8 ? orientation : NORMAL;
            }
        } catch (IOException | RuntimeException e) {
            return NORMAL;
        }
    }

    private static boolean swapsAxes(int orientation) {
        return orientation >= 5;
    }

    private static ImageDimensions upright(ImageDimensions stored, int orientation) {
        return swapsAxes(orientation)
                ? new ImageDimensions(/* width= */ stored.height(), /* height= */ stored.width())
                : stored;
    }

    private static BufferedImage flattenOnWhite(BufferedImage image) {
        BufferedImage rgb =
                new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    /** 절반씩 줄이다가 마지막에 목표 크기로 맞춘다. 한 번에 크게 줄이면 쌍선형 보간이 픽셀을 건너뛰어 거칠어진다. */
    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage current = image;
        int currentWidth = image.getWidth();
        int currentHeight = image.getHeight();
        while (currentWidth != width || currentHeight != height) {
            currentWidth = currentWidth / 2 >= width ? currentWidth / 2 : width;
            currentHeight = currentHeight / 2 >= height ? currentHeight / 2 : height;
            BufferedImage next =
                    new BufferedImage(currentWidth, currentHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = next.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(
                        RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(current, 0, 0, currentWidth, currentHeight, null);
            } finally {
                graphics.dispose();
            }
            current = next;
        }
        return current;
    }

    /** EXIF 방향(1~8)대로 픽셀을 옮겨 바로 세운다. */
    private static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation == NORMAL) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage result =
                swapsAxes(orientation)
                        ? new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB)
                        : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                switch (orientation) {
                    case 2 -> result.setRGB(w - 1 - x, y, rgb);
                    case 3 -> result.setRGB(w - 1 - x, h - 1 - y, rgb);
                    case 4 -> result.setRGB(x, h - 1 - y, rgb);
                    case 5 -> result.setRGB(y, x, rgb);
                    case 6 -> result.setRGB(h - 1 - y, x, rgb);
                    case 7 -> result.setRGB(h - 1 - y, w - 1 - x, rgb);
                    case 8 -> result.setRGB(y, w - 1 - x, rgb);
                    default -> result.setRGB(x, y, rgb);
                }
            }
        }
        return result;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
