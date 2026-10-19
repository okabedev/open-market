package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 상품(docs/glossary.md "상품", CAT-1·CAT-2). 옵션 이름, SKU, 이미지를 갖는다. 재고는 SKU마다 따로 둔 {@link Stock}이다.
 *
 * <p>입력 규칙(docs/architecture/catalog.md "규칙")을 어기면 {@link InvalidProductException}이다. 옵션 이름은 등록할 때
 * 정하고 바꾸지 않는다. 수정에서는 기존 SKU를 모두 다시 보내야 하고(가격만 바꿀 수 있다), 옵션 값이 없는 줄은 새 SKU가 된다.
 */
@Entity
@Table(name = "product")
public class Product {

    static final int NAME_MAX = 100;
    static final int DESCRIPTION_MAX = 5000;
    static final int IMAGES_MAX = 10;
    static final int OPTIONS_MAX = 2;
    static final int OPTION_NAME_MAX = 20;
    static final int OPTION_VALUE_MAX = 30;
    static final int SKUS_MAX = 100;
    static final long PRICE_MIN = 100;
    static final long PRICE_MAX = 100_000_000;
    static final long PRICE_UNIT = 10;

    @Id private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = DESCRIPTION_MAX)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "option_name_1")
    private @Nullable String optionName1;

    @Column(name = "option_name_2")
    private @Nullable String optionName2;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "file_id", nullable = false)
    private List<UUID> imageFileIds = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Sku> skus = new ArrayList<>();

    @Column(nullable = false)
    private long revision;

    @Column(name = "published_at")
    private @Nullable Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Product() {}

    private Product(
            UUID id,
            UUID sellerId,
            UUID categoryId,
            Details details,
            List<String> optionNames,
            Instant now) {
        this.id = id;
        this.sellerId = sellerId;
        this.categoryId = categoryId;
        this.status = ProductStatus.DRAFT;
        this.optionName1 = optionNames.isEmpty() ? null : optionNames.get(0);
        this.optionName2 = optionNames.size() < 2 ? null : optionNames.get(1);
        this.createdAt = now;
        apply(details, now);
    }

    /**
     * 상품 이름, 설명, 이미지.
     *
     * @param imageFileIds file의 fileId. 순서대로 보여 준다. 첫 장이 대표 이미지다
     */
    public record Details(String name, String description, List<UUID> imageFileIds) {}

    /** {@code DRAFT}로 등록한다. SKU ID는 여기서 만든다. */
    public static Product register(
            UUID id,
            UUID sellerId,
            UUID categoryId,
            Details details,
            List<String> optionNames,
            List<SkuInput> skus,
            Instant now) {
        List<String> names = checkOptionNames(optionNames);
        Product product = new Product(id, sellerId, categoryId, details, names, now);
        product.checkSkus(skus);
        skus.forEach(sku -> product.addSku(sku, now));
        return product;
    }

    /**
     * 내용을 고친다. 기존 SKU는 모두 있어야 하고 옵션 값이 같아야 한다(가격만 바뀐다). ID 없는 줄은 새 SKU다.
     *
     * @return 새로 만든 SKU. 입력 순서대로다. 호출한 쪽이 재고를 만든다
     */
    public List<Sku> update(UUID categoryId, Details details, List<SkuInput> skus, Instant now) {
        checkSkus(skus);
        Map<UUID, Sku> existing =
                this.skus.stream().collect(Collectors.toMap(Sku::id, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        for (SkuInput input : skus) {
            UUID skuId = input.skuId();
            if (skuId == null) {
                continue;
            }
            Sku sku = existing.get(skuId);
            if (sku == null || !sku.optionValues().equals(clean(input.optionValues()))) {
                throw new InvalidProductException("기존 옵션 조합의 옵션 값은 바꿀 수 없습니다.");
            }
            kept.add(skuId);
        }
        if (!kept.equals(existing.keySet())) {
            throw new InvalidProductException("기존 옵션 조합은 뺄 수 없습니다. 판매를 멈추려면 상품 판매를 중지해 주세요.");
        }
        this.categoryId = categoryId;
        apply(details, now);
        List<Sku> added = new ArrayList<>();
        for (SkuInput input : skus) {
            UUID skuId = input.skuId();
            if (skuId == null) {
                added.add(addSku(input, now));
            } else {
                Objects.requireNonNull(existing.get(skuId)).changePrice(input.price());
            }
        }
        return added;
    }

    /**
     * 판매를 시작한다. 이미지가 하나 이상 있어야 한다.
     *
     * @return 상태가 바뀌었으면 true. 이미 판매 중이면 false
     */
    public boolean publish(Instant now) {
        if (status == ProductStatus.ON_SALE) {
            return false;
        }
        if (status != ProductStatus.DRAFT) {
            throw new ProductNotPublishableException("등록 중인 상품만 판매를 시작할 수 있습니다.");
        }
        if (imageFileIds.isEmpty()) {
            throw new ProductNotPublishableException("이미지를 한 장 이상 올려야 판매를 시작할 수 있습니다.");
        }
        status = ProductStatus.ON_SALE;
        publishedAt = now;
        updatedAt = now;
        return true;
    }

    /** 이벤트를 낼 때 부른다. 올린 revision을 돌려준다. */
    public long nextRevision() {
        revision += 1;
        return revision;
    }

    public boolean isOwnedBy(UUID sellerId) {
        return this.sellerId.equals(sellerId);
    }

    private void apply(Details details, Instant now) {
        this.name = checkText(details.name(), NAME_MAX, "상품 이름은 1~100자입니다.");
        this.description = checkText(details.description(), DESCRIPTION_MAX, "상품 설명은 1~5,000자입니다.");
        List<UUID> images = details.imageFileIds();
        if (images.size() > IMAGES_MAX || new HashSet<>(images).size() != images.size()) {
            throw new InvalidProductException("이미지는 서로 다른 파일로 10장까지입니다.");
        }
        this.imageFileIds.clear();
        this.imageFileIds.addAll(images);
        this.updatedAt = now;
    }

    private Sku addSku(SkuInput input, Instant now) {
        Sku sku =
                new Sku(
                        Ids.newId(),
                        this,
                        skus.size(),
                        clean(input.optionValues()),
                        input.price(),
                        now);
        skus.add(sku);
        return sku;
    }

    /** 줄 수, 옵션 값 수와 길이, 조합 중복, 가격을 본다. 기존 SKU와 합친 개수도 상한 안이어야 한다. */
    private void checkSkus(List<SkuInput> inputs) {
        if (inputs.isEmpty() || inputs.size() > SKUS_MAX) {
            throw new InvalidProductException("옵션 조합은 1~100개입니다.");
        }
        int optionCount = optionNames().size();
        Set<List<String>> combinations = new HashSet<>();
        for (SkuInput input : inputs) {
            List<String> values = clean(input.optionValues());
            if (values.size() != optionCount) {
                throw new InvalidProductException("옵션 조합마다 옵션 이름 수만큼 값을 넣어 주세요.");
            }
            for (String value : values) {
                if (value.isEmpty() || value.length() > OPTION_VALUE_MAX) {
                    throw new InvalidProductException("옵션 값은 1~30자입니다.");
                }
            }
            if (!combinations.add(values)) {
                throw new InvalidProductException("같은 옵션 조합이 두 번 있습니다.");
            }
            long price = input.price();
            if (price < PRICE_MIN || price > PRICE_MAX || price % PRICE_UNIT != 0) {
                throw new InvalidProductException("가격은 100원부터 1억 원까지 10원 단위입니다.");
            }
        }
    }

    private static List<String> checkOptionNames(List<String> optionNames) {
        List<String> names = clean(optionNames);
        if (names.size() > OPTIONS_MAX || new HashSet<>(names).size() != names.size()) {
            throw new InvalidProductException("옵션 이름은 서로 다르게 2개까지입니다.");
        }
        for (String name : names) {
            if (name.isEmpty() || name.length() > OPTION_NAME_MAX) {
                throw new InvalidProductException("옵션 이름은 1~20자입니다.");
            }
        }
        return names;
    }

    private static List<String> clean(List<String> values) {
        return values.stream().map(String::strip).toList();
    }

    private static String checkText(String value, int max, String message) {
        String text = value.strip();
        if (text.isEmpty() || text.length() > max) {
            throw new InvalidProductException(message);
        }
        return text;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ProductStatus status() {
        return status;
    }

    public List<String> optionNames() {
        List<String> names = new ArrayList<>(2);
        if (optionName1 != null) {
            names.add(optionName1);
        }
        if (optionName2 != null) {
            names.add(optionName2);
        }
        return List.copyOf(names);
    }

    public List<UUID> imageFileIds() {
        return Collections.unmodifiableList(imageFileIds);
    }

    public List<Sku> skus() {
        return Collections.unmodifiableList(skus);
    }

    public long minPrice() {
        return skus.stream().mapToLong(Sku::price).min().orElse(0);
    }

    public long revision() {
        return revision;
    }

    public @Nullable Instant publishedAt() {
        return publishedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
