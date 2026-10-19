package com.openmarket.file.command.application;

import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.ImageVariant;
import com.openmarket.file.command.domain.InvalidImageException;
import java.util.List;

/** 이미지 읽기와 변형 만들기. 읽을 수 없는 내용이면 {@link InvalidImageException}을 던진다. */
public interface ImageProcessorPort {

    /** 픽셀을 풀지 않고 크기만 읽는다. EXIF 방향을 반영한 크기다. */
    ImageDimensions probe(byte[] content, ImageFormat format);

    /** 변형마다 JPEG을 만든다. 방향을 바로 세우고, 투명한 부분은 흰색으로 채우고, 메타데이터는 남기지 않는다. */
    List<Rendition> render(byte[] content, ImageFormat format, List<ImageVariant> variants);

    /**
     * 만든 변형 하나.
     *
     * @param jpeg {@link ImageVariant#FORMAT} 형식의 내용
     */
    // 인코딩한 내용을 그대로 저장소에 넘기는 값이라 배열 비교·복사가 필요 없다.
    @SuppressWarnings("ArrayRecordComponent")
    record Rendition(ImageVariant variant, byte[] jpeg, ImageDimensions dimensions) {}
}
