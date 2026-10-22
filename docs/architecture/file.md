# 파일 (file 서비스)

비용 0 제약 때문에 클라우드 객체 저장소를 쓰지 않는다. `file` 서비스가 로컬 볼륨에 저장하고 제공한다.
저장 위치는 `FileStoragePort` 뒤에 숨겨서, 나중에 S3 호환 저장소(예: 로컬 MinIO)로 바꿀 수 있게 한다. 이때 공개 제공을 맡는 조회 DAO(`FileContentQueryDao`)도 같이 바꾼다.

## 흐름
1. 판매자 브라우저가 BFF Route Handler를 거쳐 `POST /api/file/v1/seller/files`(multipart `file`, `purpose=PRODUCT_IMAGE`)로 올린다. 경로 접두어로 gateway와 file 둘 다 `SELLER` 역할을 검사한다. 클레임 사진은 구매자가 `POST /api/file/v1/files`(`purpose=CLAIM_IMAGE`)로 올린다. 로그인한 회원이면 된다. 경로마다 받는 용도가 정해져 있고, 다른 용도면 400 `COMMON_INVALID_REQUEST`다(판매자 경로는 `PRODUCT_IMAGE`, 회원 경로는 `CLAIM_IMAGE`와 `REVIEW_IMAGE`. 리뷰 사진은 M5 review PR에서 더한다).
2. file은 검사를 통과한 파일의 원본과 변형을 저장하고 `StoredFile`(`TEMP`)을 만든다. 응답은 `fileId`, 원본 크기, 변형 목록(이름, URL, 크기)이다. URL은 공개 파일이면 `/files/...`, 비공개 파일이면 `/api/file/v1/files/...`다(아래 "비공개 파일").
3. 클라이언트는 상품, 리뷰, 클레임을 저장할 때 `fileId`를 넣는다.
4. 소유 서비스가 발행한 이벤트에 든 `fileId`를 file이 받아 `ATTACHED`로 바꾸고 소유자를 기록한다. 소유자에서 빠진 파일은 `DETACHED`로 바꾼다(아래 "첨부").
5. 정리 작업(`FileCleanupJob`, 10분마다, ShedLock)이 24시간 지난 `TEMP`와 분리된 지 7일 지난 `DETACHED` 파일을 지운다.

## 첨부
상품 이미지(M2)와 클레임 사진(M4)을 붙인다. 리뷰 사진은 M5에서 더한다. `review.review`의 `ReviewCreated`를 받아 `fileIds`를 리뷰(소유자 `REVIEW`, 리뷰 ID)에 붙인다. 업로더가 리뷰 작성자여야 하고 용도는 `REVIEW_IMAGE`(공개, 변형 `w300`, `w800`)만이다. 리뷰는 고치지 않으므로 처음 한 번만 붙인다.
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

### 클레임 사진
- `fulfillment.claim`의 `ClaimRequested`를 받는다. `fileIds`를 클레임(소유자 `CLAIM`, 클레임 ID)에 붙인다. 업로더가 클레임의 구매자여야 하고 용도는 `CLAIM_IMAGE`만이다. 붙일 수 없는 파일은 상품과 같이 건너뛰고 경고만 남긴다.
- 클레임 사진은 신청 때 정해지고 바뀌지 않는다. 소유자 리비전은 1로 두고, 같은 클레임의 `ClaimRequested`가 다른 이벤트 ID로 다시 와도 다시 반영하지 않는다. 분리하지 않는다(거부·중재 뒤에도 근거로 남는다).
- 소유자에 클레임의 판매자(`seller_id`)를 적는다. 비공개 제공의 권한 확인에 쓴다. 상품 소유자에도 판매자를 적는다.
- 리스너 이름은 `file.claim-images`다.

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
- 공개 경로는 공개 용도(`PRODUCT_IMAGE`)의 변형만 준다. 비공개 용도면 404 `FILE_NOT_FOUND`다.

## 비공개 파일
클레임 사진(`CLAIM_IMAGE`)은 비공개다. 구매자 본인, 해당 판매자, 관리자만 본다. 변형은 상품 이미지와 같이 `w300`, `w800`을 만들고 원본은 제공하지 않는다.
| 메서드 | 경로(gateway `/api/file` 아래) | 볼 수 있는 사람 |
|---|---|---|
| GET | `/v1/files/{fileId}/{variant}` | 올린 사람(붙기 전에도 미리 보기), 파일이 붙은 클레임의 판매자 |
| GET | `/v1/admin/files/{fileId}/{variant}` | `ADMIN`. 클레임에 붙은 파일만 |

- 볼 수 없는 사람에게는 파일이 있는지 알리지 않도록 404 `FILE_NOT_FOUND`로 답한다.
- `Cache-Control: private, max-age=86400`이다. 공유 캐시에 남기지 않는다.
- 브라우저는 토큰이 없으므로 앱의 BFF가 세션의 토큰으로 이 경로를 불러 이미지를 넘긴다. shop·seller는 Route Handler `/api/claim-images/{fileId}/{variant}`, admin은 `/api/admin/claim-images/{fileId}/{variant}`(관리자 경로)가 `bff.privateFile`(`packages/bff`)을 부른다. 파일 ID와 변형 이름(`w300`, `w800`)이 아니면 부르지 않고, 로그인 전이거나 file이 이미지가 아닌 응답을 주면 모두 404다. 넘기는 헤더는 `Content-Type`과 `Cache-Control`뿐이다. 이미지 응답이라 OpenAPI 문서에서 뺀다.

## 저장소
- 컨테이너는 볼륨 `file-data`를 `/data`에 붙인다(`FILE_STORAGE_ROOT=/data`). `bootRun`은 레포의 `infra/compose/data/file`(커밋하지 않는다)을 쓴다.
- 쓸 때는 같은 디렉터리의 임시 파일에 다 쓴 뒤 이름을 바꾼다. 반쯤 쓴 파일을 제공하지 않는다.
- 메타데이터는 `file` 스키마의 `stored_file`, 변형은 `stored_file_variant`에 둔다.
- 업로드는 파일을 먼저 쓰고 메타데이터를 저장한다. 메타데이터를 저장하지 못하면 써 둔 파일을 지운다. 정리는 파일을 먼저 지우고 메타데이터를 지운다. 중간에 실패하면 메타데이터가 남아 다음 실행에서 다시 지운다.
