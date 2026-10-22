-- 클레임 사진(docs/architecture/file.md "클레임 사진", "비공개 파일"). 소유자의 판매자는 비공개 파일을 볼 수 있다.
-- 이미 있는 상품 소유자는 비워 두고 다음 상품 이벤트에서 채운다. 상품 이미지는 공개라 권한 확인에 쓰지 않는다.
ALTER TABLE file_owner ADD COLUMN seller_id uuid;
