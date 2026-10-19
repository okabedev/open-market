-- 카테고리(docs/glossary.md "카테고리"). 2단계 트리이고 상품은 소분류에만 붙는다. 관리 화면 없이 마이그레이션으로만 바꾼다(2026-10-19 결정).
CREATE TABLE category (
    id            uuid        PRIMARY KEY,
    parent_id     uuid        REFERENCES category (id),
    name          varchar(50) NOT NULL,
    display_order integer     NOT NULL
);
CREATE INDEX category_parent_idx ON category (parent_id);

-- 판매자 사본. member.seller 이벤트로 만든다(docs/architecture/catalog.md "판매자 사본").
CREATE TABLE seller (
    id         uuid        PRIMARY KEY,
    store_name varchar(30) NOT NULL,
    status     varchar(16) NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL
);

-- 상품(docs/glossary.md "상품"). 옵션 이름은 최대 2개라 컬럼으로 둔다.
CREATE TABLE product (
    id            uuid         PRIMARY KEY,
    seller_id     uuid         NOT NULL,
    category_id   uuid         NOT NULL REFERENCES category (id),
    name          varchar(100) NOT NULL,
    description   varchar(5000) NOT NULL,
    status        varchar(16)  NOT NULL,
    option_name_1 varchar(20),
    option_name_2 varchar(20),
    revision      bigint       NOT NULL,
    published_at  timestamptz,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL
);
-- 판매자 상품 목록(최근 수정 순)
CREATE INDEX product_seller_updated_idx ON product (seller_id, updated_at DESC);

-- 상품 이미지. file의 fileId를 순서대로 둔다.
CREATE TABLE product_image (
    product_id uuid    NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    position   integer NOT NULL,
    file_id    uuid    NOT NULL,
    PRIMARY KEY (product_id, position)
);

-- 옵션 조합(docs/glossary.md "옵션 조합"). 옵션 값은 상품의 옵션 이름 순서와 같다.
CREATE TABLE sku (
    id             uuid        PRIMARY KEY,
    product_id     uuid        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    position       integer     NOT NULL,
    option_value_1 varchar(30),
    option_value_2 varchar(30),
    price          bigint      NOT NULL,
    created_at     timestamptz NOT NULL
);
CREATE UNIQUE INDEX sku_product_position_uk ON sku (product_id, position);
-- 같은 상품 안에서 옵션 값 조합은 하나다.
CREATE UNIQUE INDEX sku_product_options_uk
    ON sku (product_id, coalesce(option_value_1, ''), coalesce(option_value_2, ''));

-- 재고(docs/glossary.md "재고"). SKU마다 하나. 판매 가능 수량 = on_hand - reserved.
CREATE TABLE stock (
    sku_id     uuid        PRIMARY KEY REFERENCES sku (id) ON DELETE CASCADE,
    product_id uuid        NOT NULL,
    on_hand    integer     NOT NULL CHECK (on_hand >= 0),
    reserved   integer     NOT NULL CHECK (reserved >= 0),
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL
);
CREATE INDEX stock_product_idx ON stock (product_id);

-- 카테고리 시드. ID는 고정이다(정산 수수료율이 카테고리 ID를 쓴다). 바꿀 때는 새 마이그레이션으로 이름·순서를 고치거나 새 ID를 더한다.
INSERT INTO category (id, parent_id, name, display_order) VALUES
    ('0199f000-0000-7000-8000-000000000100', NULL, '패션의류', 1),
    ('0199f000-0000-7000-8000-000000000101', '0199f000-0000-7000-8000-000000000100', '여성의류', 1),
    ('0199f000-0000-7000-8000-000000000102', '0199f000-0000-7000-8000-000000000100', '남성의류', 2),
    ('0199f000-0000-7000-8000-000000000103', '0199f000-0000-7000-8000-000000000100', '유아동의류', 3),
    ('0199f000-0000-7000-8000-000000000200', NULL, '패션잡화', 2),
    ('0199f000-0000-7000-8000-000000000201', '0199f000-0000-7000-8000-000000000200', '가방', 1),
    ('0199f000-0000-7000-8000-000000000202', '0199f000-0000-7000-8000-000000000200', '신발', 2),
    ('0199f000-0000-7000-8000-000000000203', '0199f000-0000-7000-8000-000000000200', '액세서리', 3),
    ('0199f000-0000-7000-8000-000000000300', NULL, '뷰티', 3),
    ('0199f000-0000-7000-8000-000000000301', '0199f000-0000-7000-8000-000000000300', '스킨케어', 1),
    ('0199f000-0000-7000-8000-000000000302', '0199f000-0000-7000-8000-000000000300', '메이크업', 2),
    ('0199f000-0000-7000-8000-000000000303', '0199f000-0000-7000-8000-000000000300', '헤어·바디', 3),
    ('0199f000-0000-7000-8000-000000000400', NULL, '식품', 4),
    ('0199f000-0000-7000-8000-000000000401', '0199f000-0000-7000-8000-000000000400', '신선식품', 1),
    ('0199f000-0000-7000-8000-000000000402', '0199f000-0000-7000-8000-000000000400', '가공식품', 2),
    ('0199f000-0000-7000-8000-000000000403', '0199f000-0000-7000-8000-000000000400', '음료', 3),
    ('0199f000-0000-7000-8000-000000000500', NULL, '디지털·가전', 5),
    ('0199f000-0000-7000-8000-000000000501', '0199f000-0000-7000-8000-000000000500', '휴대폰·태블릿', 1),
    ('0199f000-0000-7000-8000-000000000502', '0199f000-0000-7000-8000-000000000500', '음향기기', 2),
    ('0199f000-0000-7000-8000-000000000503', '0199f000-0000-7000-8000-000000000500', '생활가전', 3),
    ('0199f000-0000-7000-8000-000000000600', NULL, '생활·주방', 6),
    ('0199f000-0000-7000-8000-000000000601', '0199f000-0000-7000-8000-000000000600', '주방용품', 1),
    ('0199f000-0000-7000-8000-000000000602', '0199f000-0000-7000-8000-000000000600', '생활용품', 2),
    ('0199f000-0000-7000-8000-000000000603', '0199f000-0000-7000-8000-000000000600', '가구·인테리어', 3),
    ('0199f000-0000-7000-8000-000000000700', NULL, '스포츠·레저', 7),
    ('0199f000-0000-7000-8000-000000000701', '0199f000-0000-7000-8000-000000000700', '운동복', 1),
    ('0199f000-0000-7000-8000-000000000702', '0199f000-0000-7000-8000-000000000700', '캠핑', 2),
    ('0199f000-0000-7000-8000-000000000703', '0199f000-0000-7000-8000-000000000700', '헬스·요가', 3),
    ('0199f000-0000-7000-8000-000000000800', NULL, '도서·취미', 8),
    ('0199f000-0000-7000-8000-000000000801', '0199f000-0000-7000-8000-000000000800', '도서', 1),
    ('0199f000-0000-7000-8000-000000000802', '0199f000-0000-7000-8000-000000000800', '문구·오피스', 2),
    ('0199f000-0000-7000-8000-000000000803', '0199f000-0000-7000-8000-000000000800', '완구·취미', 3);
