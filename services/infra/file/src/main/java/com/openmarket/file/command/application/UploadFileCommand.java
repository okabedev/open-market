package com.openmarket.file.command.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 파일을 올린다.
 *
 * @param sizeBytes 요청이 알린 크기. 내용을 읽기 전에 한도를 넘는지 본다
 * @param content 내용. 한 번만 연다
 */
public record UploadFileCommand(UUID uploaderId, Purpose purpose, long sizeBytes, Content content) {

    /** 파일 용도. 경로마다 받는 용도가 정해져 있다(판매자는 상품 이미지, 회원은 클레임 사진과 리뷰 사진). */
    public enum Purpose {
        PRODUCT_IMAGE,
        CLAIM_IMAGE,
        REVIEW_IMAGE
    }

    /** 업로드 내용을 여는 방법. */
    @FunctionalInterface
    public interface Content {
        InputStream open() throws IOException;
    }
}
