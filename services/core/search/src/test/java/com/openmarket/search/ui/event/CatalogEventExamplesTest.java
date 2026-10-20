package com.openmarket.search.ui.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * search가 받는 catalog 이벤트마다 발행자(catalog)의 JSON 예시를 소비 쪽 페이로드로 읽는다(docs/architecture/messaging.md
 * "스키마 규칙"). 발행자가 필드를 바꾸면 여기서 먼저 깨진다. 리스너·색인 흐름은 {@code SearchIntegrationTest}가 확인한다.
 */
class CatalogEventExamplesTest {

    static final Path PUBLISHER_EXAMPLES = Path.of("../catalog/src/test/resources/events");

    /** 발행 예시에 소비 쪽이 모르는 필드가 있어도 읽는다. 소비 쪽 필드가 예시에 없으면 실패하게 한다. */
    final JsonMapper json =
            JsonMapper.builder()
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

    @ParameterizedTest
    @ValueSource(strings = {"ProductRegistered", "ProductPublished", "ProductChanged"})
    @DisplayName("상품 스냅샷 이벤트는 색인에 쓰는 필드를 모두 갖는다")
    void readsSnapshots(String type) throws IOException {
        CatalogProductEventsListener.ProductSnapshotMessage message =
                json.readValue(
                        example(type), CatalogProductEventsListener.ProductSnapshotMessage.class);

        assertThat(message.productId()).isNotNull();
        assertThat(message.revision()).isPositive();
        assertThat(message.availabilityRevision()).isPositive();
        assertThat(message.categoryPath()).isNotEmpty();
        assertThat(message.toCommand().name()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ProductStopped", "ProductResumed", "ProductBanned"})
    @DisplayName("판매 상태 이벤트는 상품 ID와 revision을 갖는다")
    void readsStatusChanges(String type) throws IOException {
        CatalogProductEventsListener.ProductStatusMessage message =
                json.readValue(
                        example(type), CatalogProductEventsListener.ProductStatusMessage.class);

        assertThat(message.productId()).isNotNull();
        assertThat(message.revision()).isPositive();
    }

    @Test
    @DisplayName("품절 전환 이벤트는 판매 가능 여부와 판매 가능 리비전을 갖는다")
    void readsStockAvailabilityChanged() throws IOException {
        StockAvailabilityChangedListener.StockAvailabilityChangedMessage message =
                json.readValue(
                        example("StockAvailabilityChanged"),
                        StockAvailabilityChangedListener.StockAvailabilityChangedMessage.class);

        assertThat(message.productId()).isNotNull();
        assertThat(message.availabilityRevision()).isPositive();
    }

    private static String example(String type) throws IOException {
        return Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json"));
    }
}
