# 파일 (file 서비스)

비용 0 제약 때문에 클라우드 객체 저장소를 쓰지 않는다. `file` 서비스가 로컬 볼륨에 저장하고 제공한다.
저장 위치는 `FileStoragePort` 뒤에 숨겨서, 나중에 S3 호환 저장소(예: 로컬 MinIO)로 바꿀 수 있게 한다. 이때 공개 제공을 맡는 조회 DAO(`FileContentQueryDao`)도 같이 바꾼다.

## 흐름
1. 판매자 브라우저가 BFF Route Handler를 거쳐 `POST /api/file/v1/seller/files`(multipart `file`, `purpose=PRODUCT_IMAGE`)로 올린다. 경로 접두어로 gateway와 file 둘 다 `SELLER` 역할을 검사한다. 리뷰·클레임 사진(`REVIEW_IMAGE`, `CLAIM_IMAGE`)은 구매자 경로와 함께 M5·M4에서 더한다.
2. file은 검사를 통과한 파일의 원본과 변형을 저장하고 `StoredFile`(`TEMP`)을 만든다. 응답은 `fileId`, 원본 크기, 변형 목록(이름, URL, 크기)이다.
3. 클라이언트는 상품, 리뷰, 클레임을 저장할 때 `fileId`를 넣는다.
4. 소유 서비스가 발행한 이벤트에 든 `fileId`를 file이 받아 `ATTACHED`로 바꾸고 소유자를 기록한다. 소유자에서 빠진 파일은 `DETACHED`로 바꾼다(아래 "첨부").
5. 정리 작업(`FileCleanupJob`, 10분마다, ShedLock)이 24시간 지난 `TEMP`와 분리된 지 7일 지난 `DETACHED` 파일을 지운다.

## 첨부
M2는 상품 이미지만 붙인다. 리뷰·클레임 사진(`ReviewCreated`, `ClaimRequested`)은 M5·M4에서 더한다.
- `catalog.product`의 `ProductRegistered`, `ProductPublished`, `ProductChanged`(상품 스냅샷)를 받는다. 스냅샷의 `imageFileIds`가 그 상품에 붙어 있어야 할 파일 전체다.
  - 목록에 있는 파일은 `ATTACHED`로 바꾸고 소유자(상품 ID)를 적는다. `DETACHED`였던 파일도 다시 붙는다(판매자가 뺐던 이미지를 7일 안에 다시 넣은 경우).
  - 그 상품에 붙어 있던 파일 중 목록에 없는 것은 `DETACHED`로 바꾸고 분리 시각을 적는다.
- 아래 파일은 붙이지 않고 건너뛴다. 이벤트는 실패로 보지 않는다. 경고 로그에 파일·상품 ID만 남긴다.
  - 없는 파일(이미 지워졌거나 잘못된 ID)
  - 업로더가 상품의 판매자가 아닌 파일. catalog는 `fileId`의 주인을 확인하지 않으므로 여기서 막는다([catalog.md](catalog.md) "규칙")
  - 용도가 다른 파일(상품에는 `PRODUCT_IMAGE`만)
  - 이미 다른 소유자에 붙은 파일
- 소유자마다 마지막으로 반영한 상품 `revision`을 `FileOwner`에 둔다. 그보다 작거나 같은 revision의 이벤트는 무시한다(재처리로 오래된 스냅샷이 다시 와도 되돌리지 않는다). 같은 이벤트는 `processed_event`로 한 번만 처리한다.
- 같은 파일을 두 상품이 동시에 붙이려 하면 `stored_file`의 버전 검사로 한쪽이 실패하고, 재시도에서 "이미 다른 소유자"로 건너뛴다.

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
