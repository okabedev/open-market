# 용어집 (유비쿼터스 언어)

문서, 코드, 대화에서 같은 이름을 쓴다. 새 용어는 코드에 넣기 전에 여기에 먼저 추가한다.
코드 이름은 클래스·이벤트·enum 이름 기준이다. 담당은 그 개념을 소유하는 서비스다.

## 계정·회원
| 용어 | 코드 이름 | 담당 | 설명 |
|---|---|---|---|
| 계정 | `Account` | auth | 로그인 수단(이메일, 비밀번호 해시)과 역할. 개인정보 프로필은 갖지 않는다 |
| 역할 | `Role` (`BUYER`, `SELLER`, `ADMIN`) | auth | 토큰의 `roles` 클레임으로 나간다 |
| 이메일 인증 | `EmailVerification` | auth | 가입 이메일 확인용 일회용 토큰. 24시간 유효. 인증을 마쳐야 주문할 수 있다(ACC-1) |
| 계정 잠금 | `Account.recordLoginFailure()` | auth | 로그인 5회 연속 실패 시 15분 동안 로그인할 수 없다(ACC-1) |
| 계정 비활성화 | `Account.deactivate()` | auth | 탈퇴한 계정. 상태 `ACTIVE` → `DEACTIVATED`. 로그인할 수 없고, 이메일은 가명으로 바꿔 같은 이메일로 다시 가입할 수 있다 |
| 회원 | `Member` | member | 계정에 딸린 프로필(이름, 연락처). `memberId`는 `accountId`와 같은 값을 쓴다 |
| 배송지 | `Address` | member | 회원당 최대 10개, 기본 배송지 1개 |
| 탈퇴 | `Member.withdraw()` | member | 상태 `ACTIVE` → `WITHDRAWN`. 개인정보를 바로 가명 처리한다(ACC-4). 판매자는 탈퇴할 수 없다 |
| 판매자 | `Seller` | member | 승인된 입점 판매자. 상호, 사업자 번호, 정산 계좌를 가진다 |
| 입점 신청 | `SellerApplication` | member | 상호, 사업자 번호, 대표자, 정산 계좌. 상태: `SUBMITTED` → `APPROVED` 또는 `REJECTED`, 신청자가 탈퇴하면 `CANCELLED`. 회원당 심사 중인 신청은 하나다 |
| 정산 계좌 | `SettlementAccount` | member | 은행, 예금주, 계좌 번호. 계좌 번호는 암호화해 저장하고 응답에는 마스킹한 값만 준다 |
| 판매자 정지 | `Seller.suspend()` | member | 상태: `ACTIVE` ↔ `SUSPENDED` |

## 상품·재고
| 용어 | 코드 이름 | 담당 | 설명 |
|---|---|---|---|
| 카테고리 | `Category` | catalog | 트리 구조. 수수료율은 정산이 카테고리 ID로 따로 관리한다 |
| 상품 | `Product` | catalog | 상태: `DRAFT` → `ON_SALE` ↔ `STOPPED`, 관리자 제재 시 `BANNED` |
| 옵션 조합 | `Sku` | catalog | 주문과 재고의 단위. 가격을 가진다. 상품의 옵션 이름(최대 2개, 예: 색상·사이즈)마다 값 하나씩을 갖는다 |
| 판매 중지 사유 | `StopReason` | catalog | `SELLER`(판매자가 멈춤, 판매자가 다시 연다), `SELLER_SUSPENDED`(판매자 정지로 멈춤, 정지가 풀리면 다시 열린다) |
| 상품 리비전 | `revision` | catalog | 상품 이벤트를 낼 때마다 1씩 오르는 번호. 소비자가 오래된 이벤트를 거른다 |
| 검색 상품 | `SearchProduct` | search | 상품 이벤트와 품절 전환 이벤트, 별점 요약, 판매량으로 만든 검색용 문서. 상품 `revision`, 판매 가능 리비전, 별점 리비전, 판매량 리비전을 따로 두고 각각 커질 때만 반영한다 |
| 판매량 | `salesCount`, `product_sales` | search | 상품의 구매확정 수량 합. search가 Postgres에서 더하고 결과를 문서에 쓴다 |
| 자동완성 | `Suggestion` | search | 검색어로 시작하는 단어가 든 판매 중 상품명 목록(SRC-2) |
| 판매자 사본 | `Seller` | catalog | member의 판매자 이벤트로 만든 사본. 상호와 판매 상태만 갖는다 |
| 재고 | `Stock` | catalog | SKU별 `onHand`(실재고)와 `reserved`(예약 수량). 판매 가능 수량 = `onHand - reserved` |
| 재고 예약 | `StockReservation` | catalog | 주문 하나에 대한 예약 묶음. 상태: `RESERVED` → `COMMITTED` 또는 `RELEASED`. 재고가 모자라 예약하지 못한 주문은 `FAILED`로 남긴다 |
| 재고 조정 | `StockAdjustment` | catalog | 판매자의 입고·차감 이력. 증감 수량과 조정 뒤 실재고를 남긴다. 클레임 재입고도 클레임 ID와 함께 남는다 |
| 재입고 | `RestockClaimUseCase` | catalog | 클레임이 끝나 돌아온 수량(`ClaimCompleted`의 `restockLines`)을 실재고에 더하는 것 |
| 판매 가능 여부 | `ProductAvailability` | catalog | 상품 단위로 판매 가능한 SKU가 하나라도 있는가(`inStock`). 바뀔 때마다 판매 가능 리비전(`availabilityRevision`)이 1씩 오른다 |
| 파일 | `StoredFile` | file | 업로드 파일 메타데이터. 상태: `TEMP` → `ATTACHED` → `DETACHED`(소유자에서 빠짐). 24시간 지난 `TEMP`와 7일 지난 `DETACHED`는 삭제 |
| 파일 소유자 | `FileOwner` | file | 파일이 붙는 대상(상품, 클레임, 리뷰). 소유자마다 마지막으로 반영한 이벤트 리비전과 판매자를 둔다 |
| 파일 용도 | `FilePurpose` | file | 업로드 목적. 저장 디렉터리, 만들 변형, 공개 여부가 정해진다. `PRODUCT_IMAGE`(공개), `CLAIM_IMAGE`(비공개), `REVIEW_IMAGE`(공개) |
| 이미지 변형 | `ImageVariant` | file | 공개로 제공하는 썸네일. 폭 300px(`w300`), 800px(`w800`) JPEG. 원본은 제공하지 않는다 |

## 주문·결제
| 용어 | 코드 이름 | 담당 | 설명 |
|---|---|---|---|
| 장바구니 | `Cart`, `CartItem` | order | 회원당 하나 |
| 주문 | `Order` | order | 구매자의 결제 단위. 상태: `PENDING`(예약 중) → `AWAITING_PAYMENT` → `PAID`, 또는 `CANCELLED`. 화면 이름은 재고 확인 중, 결제 대기, 결제 완료, 취소됨이다(하위 주문도 같다) |
| 하위 주문 | `SellerOrder` | order | 주문을 판매자별로 나눈 단위. 배송, 클레임, 정산의 기준이다 |
| 하위 주문 진행 상태 | `SellerOrderProgress` | order | 결제된 하위 주문의 배송 진행. `PAID` → `ACCEPTED` → `SHIPPED` → `IN_TRANSIT` → `DELIVERED` → `CONFIRMED`, 발송 전 취소가 끝나면 `CANCELLED`, 반품이 끝나면 `RETURNED`. fulfillment 배송·클레임 이벤트로 바뀐다 |
| 주문 항목 | `OrderLine` | order | SKU 하나와 수량, 주문 당시 가격 스냅샷 |
| 취소 사유 | `CancelReason` | order | 결제 전 주문 취소의 이유. `STOCK_UNAVAILABLE`(재고 예약 실패), `COUPON_UNAVAILABLE`(쿠폰 예약 실패), `PAYMENT_TIMEOUT`(주문 만료), `BUYER_CANCELLED`(구매자 취소) |
| 주문 만료 | `OrderExpiryJob` | order | 결제 기한(주문 후 15분) 안에 결제하지 않은 주문. 취소 사유 `PAYMENT_TIMEOUT`으로 `CANCELLED`가 된다 |
| 결제 | `Payment` | payment | 주문 하나당 하나. 상태: `READY` → `IN_PROGRESS`(토스 승인 요청 중) → `APPROVED` → `PARTIALLY_CANCELLED` 또는 `CANCELLED`. 승인 실패 시 `FAILED`(주문 결제 기한 안에 다시 승인할 수 있다), 결제 전 주문 취소 시 `READY` → `CANCELLED` |
| 결제 키 | `paymentKey` | payment | 토스페이먼츠가 발급한 결제 식별자 |
| 결제 원장 | `PaymentLedgerEntry` | payment | 승인, 조회, 취소 요청과 PG 응답의 추가 전용 기록 |
| 결제 재조회 | `PaymentReconcileJob` | payment | 승인 결과를 확인하지 못한 `IN_PROGRESS` 결제를 토스 결제 조회로 확정하는 작업 |
| 보상 취소 | `PaymentCancelJob` | payment | 결제 전에 취소된 주문의 승인된 결제를 토스에서 전액 취소하는 작업(PAY-3). 결과로 `PaymentCancelled`를 낸다 |
| 환불 | `Refund` | payment | 하위 주문 단위 부분취소. 클레임 ID로 멱등 처리한다. 상태: `REQUESTED` → `COMPLETED` 또는 `FAILED`. 토스 호출은 환불 작업(`RefundJob`)이 한다 |
| 금액 | `Money` | 공통 | 원(KRW) 단위 `long`. 소수점은 쓰지 않는다 |

## 배송·클레임
| 용어 | 코드 이름 | 담당 | 설명 |
|---|---|---|---|
| 배송 | `Shipment` | fulfillment | 하위 주문 하나당 하나(교환 재발송은 새 배송). 상태: `READY` → `ACCEPTED`(발주 확인) → `SHIPPED` → `IN_TRANSIT` → `DELIVERED`. 발송 전 취소 클레임이면 `CANCELLED`. 화면 이름은 결제 완료, 상품 준비 중, 발송, 배송 중, 배송 완료, 취소됨이다 |
| 배송 시뮬레이터 | `ShipmentSimulatorJob` | fulfillment | 택배사 연동 대신 발송한 배송을 시간이 지나면 배송 중, 배송 완료로 바꾸는 작업 |
| 송장 | `TrackingInfo` | fulfillment | 택배사(`Carrier`) 코드와 송장 번호 |
| 구매확정 | `PurchaseConfirmation` | fulfillment | 하위 주문당 한 번. 구매자 확정, 또는 배송 완료 7일 뒤 자동 확정(`PurchaseConfirmJob`). 이후에는 클레임을 신청할 수 없다 |
| 클레임 | `Claim` | fulfillment | 하위 주문 전체가 대상이다. 유형: `CANCEL`(발송 전 취소), `RETURN`(반품), `EXCHANGE`(같은 SKU 교환). 상태: `REQUESTED` → `APPROVED` → `COMPLETED`, 또는 `REJECTED` → `MEDIATING`(중재) → `APPROVED` 또는 `REJECTED` |
| 클레임 사유 | `ClaimReason` | fulfillment | `CHANGE_OF_MIND`, `DEFECTIVE`, `WRONG_ITEM`, `NOT_AS_DESCRIBED`, `BUYER_CANCEL`. 사유가 귀책을 정한다 |
| 귀책 | `Fault` (`BUYER`, `SELLER`) | fulfillment | 반품 배송비 차감 여부와 교환 가능 여부를 정한다 |
| 반품 배송비 | `returnShippingFee` | fulfillment | 구매자 귀책 반품의 환불에서 빼는 플랫폼 고정 금액(왕복) |
| 중재 | `MEDIATING` | fulfillment | 판매자가 거부한 반품·교환을 구매자가 관리자에게 다시 판단해 달라고 요청한 상태 |

## 쿠폰·리뷰·정산·알림
| 용어 | 코드 이름 | 담당 | 설명 |
|---|---|---|---|
| 쿠폰 정책 | `Coupon` | promotion | 할인 방식(`FIXED`, `RATE`), 최소 주문 금액, 최대 할인, 발급 수량, 발급 기간, 사용 기한(발급 후 일수). 발행 주체(`CouponIssuer`)는 `PLATFORM`(관리자) 또는 `SELLER`. 만든 뒤 바꾸지 않는다. 상태: `ISSUING` → `STOPPED`(발급 중지) |
| 플랫폼 쿠폰 | `CouponIssuer.PLATFORM` | promotion | 관리자가 만들고 플랫폼이 부담하는 쿠폰. 주문에 한 장 |
| 판매자 쿠폰 | `CouponIssuer.SELLER` | promotion | 판매자가 만들고 판매자가 부담하는 쿠폰. 그 판매자의 모든 상품에 쓰고, 하위 주문에 한 장. 정산에서 판매자 쿠폰 부담분으로 뺀다 |
| 발급 쿠폰 | `IssuedCoupon` | promotion | 회원이 내려받은 쿠폰 한 장. 상태: `AVAILABLE` → `RESERVED` → `USED`, 결제 전 취소로 복원하면 `AVAILABLE`. 만료는 상태가 아니라 만료 시각으로 정한다 |
| 선착순 발급 | `Coupon.issue()` + Redis Lua 스크립트 | promotion | 수량과 1인 1매를 Redis에서 원자적으로 확인한다. DB 유니크가 1인 1매의 마지막 방어선이다 |
| 하위 주문 결제 금액 | `paymentAmount` | order | 하위 주문 상품 금액 − 판매자 쿠폰 할인 − 플랫폼 쿠폰 할인 몫. 환불의 기준이다 |
| 리뷰 | `Review` | review | 주문 항목(`OrderLine`)당 하나. 상태: `VISIBLE` ↔ `HIDDEN` |
| 리뷰 작성 자격 | `ReviewEligibility` | review | 구매확정 이벤트로 주문 항목마다 생긴다. 리뷰를 쓰면 리뷰 ID가 남는다 |
| 답글 | `ReviewReply` | review | 판매자가 리뷰에 다는 글. 리뷰마다 하나이고 고칠 수 있다 |
| 별점 요약 | `RatingSummary` | review | 상품별 보이는 리뷰 수, 별점 합, 리비전. 평균은 소수 첫째 자리로 반올림해 보인다 |
| 정산 대상 | `SettlementItem` | settlement | 구매확정된 하위 주문 하나. 판매 금액, 수수료, 판매자 쿠폰 부담분, 정산 금액, 정산 기간을 가진다. 아직 확정되지 않은 대상이 정산 예정 내역이다 |
| 정산 기간 | `SettlementPeriod` | settlement | 기준 시각(월요일 0시)에서 설정 길이(기본 7일)씩 나눈 `[시작, 끝)` 구간 |
| 정산 | `Settlement` | settlement | 판매자·정산 기간마다 하나인 확정 묶음. 상태: `CONFIRMED`(배치 확정) → `PAID_OUT`(지급 완료 기록) |
| 수수료율 | `CommissionRate` | settlement | 소분류 카테고리별 요율(bp). 없으면 기본 요율(10%) |
| 알림 | `Notification` | notification | 채널: `EMAIL`, `IN_APP`. 상태: `PENDING` → `SENT`, 다섯 번 실패하면 `FAILED`, 받을 회원이 없거나 이메일을 인증하지 않았으면 `SKIPPED`. 원인 이벤트 ID로 중복을 막는다 |
| 받는 쪽 | `NotificationAudience` | notification | 인앱 알림을 보이는 앱: `BUYER`(shop), `SELLER`(seller), `ADMIN`(admin, 관리자 전체가 함께 보는 역할 대상 알림) |
| 알림 읽음 | `NotificationRead` | notification | 회원이 인앱 알림을 읽은 기록. 관리자 알림은 관리자마다 따로 남는다 |

## 기술 용어
| 용어 | 코드 이름 | 설명 |
|---|---|---|
| 아웃박스 | `outbox_event` 테이블, `Outbox` | 도메인 변경과 같은 트랜잭션에 쌓는 발행 대기 이벤트. Debezium이 읽어 Kafka로 보낸다 |
| 처리한 이벤트 | `processed_event` 테이블 | 소비자가 처리한 이벤트 ID. 중복 소비를 막는다 |
| 멱등 소비자 | `IdempotentEventProcessor` | `processed_event` 기록과 리스너 처리를 한 트랜잭션으로 묶는다 |
| 멱등성 키 | `Idempotency-Key` 헤더, `idempotency_record` 테이블 | 상태 변경 API의 중복 요청을 막는다 |
| 이벤트 봉투 | `EventEnvelope` | 이벤트 공통 필드. [messaging.md](architecture/messaging.md) 참고 |
| DLT | `<topic>.DLT` | 재시도 후에도 실패한 메시지가 가는 토픽 |
| 감사 로그 | `AuditLog`, `audit_log` 테이블 | 관리자 작업을 누가, 언제, 무엇에 했는지 남긴다(NFR-8). 작업과 같은 트랜잭션에 쓴다 |
| DLT 메시지 | `DeadLetter`, `dead_letter` 테이블 | 리스너 그룹 하나가 처리하지 못해 DLT로 간 메시지. 그 그룹의 서비스가 모아 둔다. 상태는 `PENDING`, `RETRIED`(다시 보냄), `DISCARDED`(버림) |
| 다시 보내기 | `retry`, `dlt-target-group` 헤더 | DLT 메시지를 원래 토픽으로 다시 보내 실패한 그룹만 처리하게 한다(ADM-5) |
| 운영 지표 | `OrderMetrics`, `PaymentMetrics` | 관리자가 보는 일별 주문 수, 결제된 주문 수, GMV, 결제 실패율(ADM-4). 업무 DB에서 센다 |
| GMV | `gmv` | 총 거래액. 그날 결제된 주문의 결제 금액 합이다. 뒤의 취소·환불을 빼지 않는다 |
| 결제 실패율 | `failureRate` | 결과가 정해진 승인 시도 중 거절된 비율. 결제 상태가 아니라 결제 원장으로 센다 |
| 2단계 인증 | TOTP, `RecoveryCode` | 관리자가 비밀번호 다음에 확인하는 6자리 시간 기반 코드. 기기를 잃었을 때는 한 번 쓰는 복구 코드를 쓴다 |
| 장애 주입 | `fault` | Kafka 중지, DB 지연, PG 지연을 일부러 넣어 정합성과 격리를 확인하는 시험 |
| 런북 | `docs/runbook.md` | 장애 유형별 확인과 대응 절차 |
