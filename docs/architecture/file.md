# 파일 (file 서비스)

비용 0 제약 때문에 클라우드 객체 저장소를 쓰지 않는다. `file` 서비스가 로컬 볼륨에 저장하고 제공한다.
저장 위치는 `StoragePort` 뒤에 숨겨서, 나중에 S3 호환 저장소(예: 로컬 MinIO)로 바꿀 수 있게 한다.

## 흐름
1. 브라우저가 BFF를 거쳐 `POST /api/file/v1/files`(multipart)로 올린다. 용도(`PRODUCT_IMAGE`, `REVIEW_IMAGE`, `CLAIM_IMAGE`)를 함께 보낸다.
2. file은 검사를 통과한 파일을 저장하고 `StoredFile`(`TEMP`)을 만든다. 응답으로 `fileId`와 미리보기 URL을 준다.
3. 클라이언트는 상품, 리뷰, 클레임을 저장할 때 `fileId`를 넣는다.
4. 소유 서비스가 발행한 이벤트(`ProductPublished`, `ProductChanged`, `ReviewCreated`, `ClaimRequested`)에 든 `fileId`를 file이 받아 `ATTACHED`로 바꾼다. 소유자 ID와 함께 기록한다. 상품 수정으로 빠진 이미지는 `DETACHED`로 바꾸고 나중에 지운다.
5. 24시간 지난 `TEMP` 파일과 7일 지난 `DETACHED` 파일은 정리 작업이 지운다.

## 검사
- 크기 제한: 이미지 10MB.
- 확장자가 아니라 파일 내용(매직 바이트)으로 형식을 판별한다. 허용 형식은 JPEG, PNG, WebP다.
- 업로더당 레이트 리밋을 건다.
- 저장 경로에는 사용자 입력을 쓰지 않는다. `<용도>/<yyyy>/<MM>/<fileId>.<ext>`로 만든다.

## 썸네일과 제공
- 상품 이미지는 업로드할 때 썸네일(가로 300px, 800px)을 만든다. 라이브러리는 M2에서 정한다.
- 공개 이미지(상품, 리뷰)는 gateway `/files/{fileId}/{variant}`로 인증 없이 제공한다. `Cache-Control`은 길게 주고, 파일이 바뀌면 새 `fileId`를 쓴다.
- 클레임 사진은 비공개다. 구매자 본인, 해당 판매자, 관리자만 볼 수 있다.

## 저장소
- Compose 볼륨 `file-data`를 file 컨테이너의 `/data`에 붙인다. IDE로 실행할 때는 `infra/compose/data/file`(git 제외)을 쓴다.
- 메타데이터(`StoredFile`)는 `file` 스키마에 둔다.
