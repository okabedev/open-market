package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Category;
import com.openmarket.catalog.command.domain.CategoryRepository;
import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductAvailability;
import com.openmarket.catalog.command.domain.ProductAvailabilityRepository;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.ProductSnapshot;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockAdjustment;
import com.openmarket.catalog.command.domain.StockAdjustmentRepository;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** catalog 유스케이스 테스트용 가짜 구현. */
final class CatalogFakes {

    static final UUID FASHION = UUID.fromString("0199f000-0000-7000-8000-000000000100");
    static final UUID WOMEN = UUID.fromString("0199f000-0000-7000-8000-000000000101");

    private CatalogFakes() {}

    static final class Products implements ProductRepository {
        final Map<UUID, Product> rows = new LinkedHashMap<>();

        @Override
        public Product save(Product product) {
            rows.put(product.id(), product);
            return product;
        }

        @Override
        public Optional<Product> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Product> findForUpdateById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<Product> findForUpdateBySellerId(UUID sellerId) {
            return rows.values().stream().filter(p -> p.sellerId().equals(sellerId)).toList();
        }
    }

    static final class Stocks implements StockRepository {
        final Map<UUID, Stock> rows = new LinkedHashMap<>();

        @Override
        public Stock save(Stock stock) {
            rows.put(stock.skuId(), stock);
            return stock;
        }

        @Override
        public Optional<Stock> findForUpdateBySkuId(UUID skuId) {
            return Optional.ofNullable(rows.get(skuId));
        }

        @Override
        public List<Stock> findByProductId(UUID productId) {
            return rows.values().stream().filter(s -> s.productId().equals(productId)).toList();
        }
    }

    static final class Availabilities implements ProductAvailabilityRepository {
        final Map<UUID, ProductAvailability> rows = new HashMap<>();

        @Override
        public ProductAvailability save(ProductAvailability availability) {
            rows.put(availability.productId(), availability);
            return availability;
        }

        @Override
        public Optional<ProductAvailability> findByProductId(UUID productId) {
            return Optional.ofNullable(rows.get(productId));
        }

        @Override
        public Optional<ProductAvailability> findForUpdateByProductId(UUID productId) {
            return Optional.ofNullable(rows.get(productId));
        }
    }

    static final class Adjustments implements StockAdjustmentRepository {
        final List<StockAdjustment> rows = new ArrayList<>();

        @Override
        public StockAdjustment save(StockAdjustment adjustment) {
            rows.add(adjustment);
            return adjustment;
        }
    }

    /** 시드와 같은 패션의류 > 여성의류만 둔다. */
    static final class Categories implements CategoryRepository {
        final Map<UUID, Category> rows = new HashMap<>();

        Categories() {
            rows.put(FASHION, new Category(FASHION, null, "패션의류", 1));
            rows.put(WOMEN, new Category(WOMEN, FASHION, "여성의류", 1));
        }

        @Override
        public Optional<Category> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean existsByParentId(UUID parentId) {
            return rows.values().stream().anyMatch(c -> parentId.equals(c.parentId()));
        }
    }

    static final class Sellers implements SellerRepository {
        final Map<UUID, Seller> rows = new HashMap<>();

        @Override
        public Seller save(Seller seller) {
            rows.put(seller.id(), seller);
            return seller;
        }

        @Override
        public Optional<Seller> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }
    }

    static final class RecordingOutbox implements Outbox {
        final List<Appended> events = new ArrayList<>();

        @Override
        public UUID append(
                String aggregateType,
                String aggregateId,
                String type,
                int schemaVersion,
                Object payload) {
            events.add(new Appended(aggregateType, aggregateId, type, payload));
            return UUID.randomUUID();
        }

        List<String> types() {
            return events.stream().map(Appended::type).toList();
        }

        ProductSnapshot lastSnapshot() {
            return (ProductSnapshot) events.getLast().payload();
        }
    }

    static final class RecordingAuditLog implements AuditLog {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entries.add(entry);
        }
    }

    record Appended(String aggregateType, String aggregateId, String type, Object payload) {}

    static <T> @Nullable T first(Collection<T> values) {
        return values.isEmpty() ? null : values.iterator().next();
    }
}
