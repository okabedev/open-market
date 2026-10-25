package com.openmarket.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.settlement.command.application.CreateSettlementItemCommand;
import com.openmarket.settlement.command.application.CreateSettlementItemUseCase;
import com.openmarket.settlement.infra.SettlementJob;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정산 작업(docs/architecture/settlement.md "정산 확정"): 멱등(STL-4), 늦게 온 대상, 판매자 단위 잠금. 대상은 지난 기간(마감 지연이
 * 지난)에 구매확정된 것으로 만든다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresContainerConfiguration.class)
class SettlementJobIntegrationTest {

    @Autowired SettlementJob job;
    @Autowired CreateSettlementItemUseCase createItem;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;

    // 기본 7일 기간에서 2주 전은 끝난 지 마감 지연(3시간)이 지난 기간이다
    final Instant twoWeeksAgo = Instant.now().minus(Duration.ofDays(14));

    @Test
    @DisplayName("작업을 두 번 돌려도 정산은 판매자·기간마다 하나이고 금액이 같다. SettlementConfirmed는 예시와 같은 필드다")
    void confirmsOnce() throws Exception {
        UUID seller = UUID.randomUUID();
        purchase(seller, twoWeeksAgo, 30_000, 2_000);
        purchase(seller, twoWeeksAgo, 10_000, 0);

        job.confirm();
        String first = settlementOf(seller);
        job.confirm();

        assertThat(settlementOf(seller))
                .isEqualTo(first)
                .isEqualTo("1/2/40000/4000/2000/34000/CONFIRMED");
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM settlement_item i JOIN settlement s ON"
                                                + " s.id = i.settlement_id WHERE s.seller_id = :id")
                                .param("id", seller)
                                .query(Integer.class)
                                .single())
                .isEqualTo(2);
        String settlementId =
                jdbc.sql("SELECT id::text FROM settlement WHERE seller_id = :id")
                        .param("id", seller)
                        .query(String.class)
                        .single();
        List<String> payloads =
                jdbc.sql(
                                "SELECT payload::text FROM outbox_event WHERE aggregate_type ="
                                        + " 'settlement.settlement' AND aggregate_id = :id")
                        .param("id", settlementId)
                        .query(String.class)
                        .list();
        assertThat(payloads).hasSize(1);
        JsonNode payload = json.readTree(payloads.getFirst());
        assertThat(payload.propertyNames())
                .isEqualTo(example("SettlementConfirmed").propertyNames());
        assertThat(payload.get("amount").asLong()).isEqualTo(34_000);
        assertThat(payload.get("commission").asLong()).isEqualTo(4_000);
    }

    @Test
    @DisplayName("확정된 기간에 늦게 온 대상은 다음 기간에 들어가고 확정한 정산은 바뀌지 않는다")
    void movesLateItem() {
        // 3주 전 기간의 다음 기간도 끝난 지 마감 지연이 지났다
        Instant threeWeeksAgo = Instant.now().minus(Duration.ofDays(21));
        UUID seller = UUID.randomUUID();
        purchase(seller, threeWeeksAgo, 30_000, 0);
        job.confirm();

        UUID late = purchase(seller, threeWeeksAgo, 9_000, 0);
        job.confirm();

        assertThat(settlementsOf(seller)).containsExactly("30000", "9000");
        assertThat(
                        jdbc.sql(
                                        "SELECT i.period_start = s.period_end FROM settlement_item i,"
                                                + " settlement s WHERE i.seller_order_id = :late AND"
                                                + " s.seller_id = :seller AND s.sales_amount = 30000")
                                .param("late", late)
                                .param("seller", seller)
                                .query(Boolean.class)
                                .single())
                .isTrue();
    }

    @Test
    @DisplayName("같은 판매자의 대상 만들기와 확정이 겹쳐도 확정한 정산 금액은 그 정산의 대상 합과 같고, 빠진 대상은 다음 기간에 있다")
    void serializesPerSeller() throws Exception {
        UUID seller = UUID.randomUUID();
        purchase(seller, twoWeeksAgo, 1_000, 0);
        int writers = 8;
        CyclicBarrier barrier = new CyclicBarrier(writers + 2);
        ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                futures.add(
                        pool.submit(
                                (Callable<Void>)
                                        () -> {
                                            barrier.await(10, TimeUnit.SECONDS);
                                            purchase(seller, twoWeeksAgo, 1_000, 0);
                                            return null;
                                        }));
            }
            for (int i = 0; i < 2; i++) {
                futures.add(
                        pool.submit(
                                (Callable<Void>)
                                        () -> {
                                            barrier.await(10, TimeUnit.SECONDS);
                                            job.confirm();
                                            return null;
                                        }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        job.confirm();

        // 정산마다 금액 = 그 정산에 들어간 대상의 합, 대상 수도 같다
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM settlement s WHERE s.seller_id = :id AND"
                                                + " (s.sales_amount, s.item_count) <> (SELECT"
                                                + " coalesce(sum(i.sales_amount), 0), count(*) FROM"
                                                + " settlement_item i WHERE i.settlement_id = s.id)")
                                .param("id", seller)
                                .query(Integer.class)
                                .single())
                .isZero();
        // 대상 9개가 모두 정산됐고(빠진 대상은 다음 기간으로 가 아직 끝나지 않았을 수 있다) 기간마다 정산은 하나다
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM settlement_item WHERE seller_id = :id AND"
                                                + " (settlement_id IS NOT NULL OR period_start >"
                                                + " :twoWeeksAgo)")
                                .param("id", seller)
                                .param("twoWeeksAgo", java.sql.Timestamp.from(twoWeeksAgo))
                                .query(Integer.class)
                                .single())
                .isEqualTo(writers + 1);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) = count(DISTINCT period_start) FROM settlement"
                                                + " WHERE seller_id = :id")
                                .param("id", seller)
                                .query(Boolean.class)
                                .single())
                .isTrue();
    }

    private UUID purchase(UUID sellerId, Instant confirmedAt, long amount, long couponDiscount) {
        UUID sellerOrderId = UUID.randomUUID();
        createItem.execute(
                new CreateSettlementItemCommand(
                        UUID.randomUUID(),
                        sellerOrderId,
                        sellerId,
                        confirmedAt,
                        couponDiscount,
                        List.of(
                                new CreateSettlementItemCommand.Line(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        1,
                                        amount))));
        return sellerOrderId;
    }

    private String settlementOf(UUID sellerId) {
        return jdbc.sql(
                        "SELECT count(*) OVER () || '/' || item_count || '/' || sales_amount || '/' ||"
                                + " commission_amount || '/' || seller_coupon_discount || '/' ||"
                                + " amount || '/' || status FROM settlement WHERE seller_id = :id")
                .param("id", sellerId)
                .query(String.class)
                .single();
    }

    private List<String> settlementsOf(UUID sellerId) {
        return jdbc.sql(
                        "SELECT sales_amount::text FROM settlement WHERE seller_id = :id ORDER BY"
                                + " period_start")
                .param("id", sellerId)
                .query(String.class)
                .list();
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
