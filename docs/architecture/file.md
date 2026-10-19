# 파일 (file 서비스)

비용 0 제약 때문에 클라우드 객체 저장소를 쓰지 않는다. `file` 서비스가 로컬 볼륨에 저장하고 제공한다.
저장 위치는 `FileStoragePort` 뒤에 숨겨서, 나중에 S3 호환 저장소(예: 로컬 MinIO)로 바꿀 수 있게 한다. 이때 공개 제공을 맡는 조회 DAO(`FileContentQueryDao`)도 같이 바꾼다.

## 흐름
1. 판매자 브라우저가 BFF Route Handler를 거쳐 `POST /api/file/v1/seller/files`(multipart `file`, `purpose=PRODUCT_IMAGE`)로 올린다. 경로 접두어로 gateway와 file 둘 다 `SELLER` 역할을 검사한다. 리뷰·클레임 사진(`REVIEW_IMAGE`, `CLAIM_IMAGE`)은 구매자 경로와 함께 M5·M4에서 더한다.
2. file은 검사를 통과한 파일의 원본과 변형을 저장하고 `StoredFile`(`TEMP`)을 만든다. 응답은 `fileId`, 원본 크기, 변형 목록(이름, URL, 크기)이다.
3. 클라이언트는 상품, 리뷰, 클레임을 저장할 때 `fileId`를 넣는다.
4. 소유 서비스가 발행한 이벤트(`ProductPublished`, `ProductChanged`, `ReviewCreated`, `ClaimRequested`)에 든 `fileId`를 file이 받아 `ATTACHED`로 바꾼다. 소유자 ID와 함께 기록한다. 상품 수정으로 빠진 이미지는 `DETACHED`로 바꾸고 나중에 지운다(M2 첨부 PR).
5. 24시간 지난 `TEMP` 파일은 정리 작업(`TempFileCleanupJob`, 10분마다, ShedLock)이 지운다. 7일 지난 `DETACHED` 파일은 첨부 PR에서 같은 방식으로 지운다.

## 검사
싼 검사부터 한다. 규칙은 `UploadPolicy`에 있다.
| 순서 | 검사 | 실패 |
|---|---|---|
| 1 | 업로더가 지난 1시간에 올린 파일이 200개 미만 | 429 `FILE_UPLOAD_QUOTA_EXCEEDED` |
| 2 | 크기 10MB 이하. multipart 한도가 본문을 다 받기 전에 끊고, 유스케이스가 내용을 읽으며 한 번 더 본다 | 413 `COMMON_PAYLOAD_TOO_LARGE`, 400 `FILE_TOO_LARGE` |
| 3 | 확장자가 아니라 내용의 첫 바이트(매직 바이트)로 JPEG, PNG, WebP를 판별 | 400 `FILE_UNSUPPORTED_FORMAT` |
| 4 | 머리만 읽은 크기가 5천만 화소 이하(압축 폭탄 방지) | 400 `FILE_IMAGE_TOO_LARGE` |
| 5 | 변형을 만들 수 있는지(손상, 잘림) | 400 `FILE_INVALID_IMAGE` |

- 저장 경로에는 사용자 입력(파일 이름 포함)을 쓰지 않는다. 원본은 `<용도>/<yyyy>/<MM>/<fileId>.<ext>`, 변형은 같은 디렉터리의 `<fileId>.<변형>.jpg`다. 파일 이름은 저장하지 않는다.
- gateway 기본 레이트 리밋(사용자마다 초당 20회)도 함께 걸린다.

## 썸네일과 제공
- 상품 이미지는 업로드할 때 변형 `w300`, `w800`(폭 300px, 800px, 높이는 비율대로)을 JPEG으로 만든다. 원본이 더 작으면 늘리지 않는다. 처리 방식과 라이브러리는 [ADR 0017](../adr/0017-image-processing.md).
  - JPEG의 EXIF 방향을 반영해 바로 세운다. 응답의 원본 크기도 바로 세운 크기다.
  - 변형은 다시 인코딩하므로 EXIF(GPS 등)가 남지 않는다. 투명한 부분은 흰색으로 채운다.
- 원본은 저장만 하고 제공하지 않는다(EXIF에 위치 정보가 있을 수 있다).
- 공개 변형은 gateway `GET /files/{fileId}/{variant}`로 인증 없이 제공한다. gateway와 file의 공개 경로 설정 양쪽에 적는다([auth.md](auth.md) "인가"). `Cache-Control: max-age=31536000, public, immutable`이다. 내용이 바뀌면 새 `fileId`를 쓴다.
- 이 경로는 OpenAPI 문서에서 뺀다. 브라우저가 `<img>`로 받고, api-client는 `/api/file` 아래만 부른다.
- 클레임 사진은 비공개다. 구매자 본인, 해당 판매자, 관리자만 볼 수 있다(M4).

## 저장소
- 컨테이너는 볼륨 `file-data`를 `/data`에 붙인다(`FILE_STORAGE_ROOT=/data`). `bootRun`은 레포의 `infra/compose/data/file`(커밋하지 않는다)을 쓴다.
- 쓸 때는 같은 디렉터리의 임시 파일에 다 쓴 뒤 이름을 바꾼다. 반쯤 쓴 파일을 제공하지 않는다.
- 메타데이터는 `file` 스키마의 `stored_file`, 변형은 `stored_file_variant`에 둔다.
- 업로드는 파일을 먼저 쓰고 메타데이터를 저장한다. 메타데이터를 저장하지 못하면 써 둔 파일을 지운다. 정리는 파일을 먼저 지우고 메타데이터를 지운다. 중간에 실패하면 메타데이터가 남아 다음 실행에서 다시 지운다.
