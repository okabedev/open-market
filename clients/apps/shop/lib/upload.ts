/**
 * 구매자가 BFF(`/api/files?purpose=`)로 올리는 사진의 용도와 크기 상한. 회원 업로드 경로가 받는 용도만 둔다. 원본은 file 규칙이다
 * (docs/architecture/file.md "흐름", "검사"). 클라이언트 폼도 쓰므로 서버 전용 모듈을 import하지 않는다.
 */
export const UPLOAD_PURPOSES = ["CLAIM_IMAGE", "REVIEW_IMAGE"] as const;
export type UploadPurpose = (typeof UPLOAD_PURPOSES)[number];

export const PHOTO_MAX_BYTES = 10 * 1024 * 1024;

export function isUploadPurpose(value: unknown): value is UploadPurpose {
  return UPLOAD_PURPOSES.some((purpose) => purpose === value);
}
