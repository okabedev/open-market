package com.openmarket.catalog.ui.web;

import static com.openmarket.catalog.ui.web.SellerProductQueryController.sellerId;

import com.openmarket.catalog.command.application.AdjustStockCommand;
import com.openmarket.catalog.command.application.AdjustStockUseCase;
import com.openmarket.catalog.command.application.ChangeSaleStatusCommand;
import com.openmarket.catalog.command.application.PublishProductCommand;
import com.openmarket.catalog.command.application.PublishProductUseCase;
import com.openmarket.catalog.command.application.RegisterProductCommand;
import com.openmarket.catalog.command.application.RegisterProductUseCase;
import com.openmarket.catalog.command.application.ResumeProductUseCase;
import com.openmarket.catalog.command.application.StopProductUseCase;
import com.openmarket.catalog.command.application.UpdateProductCommand;
import com.openmarket.catalog.command.application.UpdateProductUseCase;
import com.openmarket.common.web.idempotency.Idempotent;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 상품 등록·수정·판매 시작·중지·재개, 재고 조정(CAT-1~3). 주체는 토큰의 {@code sub}(= sellerId)다. 입력 규칙 위반은 400 {@code
 * CATALOG_INVALID_INPUT}, 다른 판매자의 상품은 404 {@code CATALOG_PRODUCT_NOT_FOUND}.
 */
@RestController
@RequestMapping("/v1/seller/products")
public class SellerProductCommandController {

    private final RegisterProductUseCase register;
    private final UpdateProductUseCase update;
    private final PublishProductUseCase publish;
    private final StopProductUseCase stop;
    private final ResumeProductUseCase resume;
    private final AdjustStockUseCase adjustStock;

    public SellerProductCommandController(
            RegisterProductUseCase register,
            UpdateProductUseCase update,
            PublishProductUseCase publish,
            StopProductUseCase stop,
            ResumeProductUseCase resume,
            AdjustStockUseCase adjustStock) {
        this.register = register;
        this.update = update;
        this.publish = publish;
        this.stop = stop;
        this.resume = resume;
        this.adjustStock = adjustStock;
    }

    /**
     * 상품을 {@code DRAFT}로 등록한다. {@code Idempotency-Key}가 필요하다. 입점 승인이 아직 catalog에 반영되지 않았으면 409
     * {@code CATALOG_SELLER_NOT_READY}(잠시 뒤 다시 한다).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public RegisterProductResponse register(
            Principal principal, @Valid @RequestBody RegisterProductRequest request) {
        return new RegisterProductResponse(
                register.execute(
                        new RegisterProductCommand(
                                sellerId(principal),
                                request.categoryId(),
                                request.name(),
                                request.description(),
                                request.imageFileIds(),
                                Objects.requireNonNullElse(request.optionNames(), List.of()),
                                request.skus().stream()
                                        .map(
                                                sku ->
                                                        new RegisterProductCommand.Sku(
                                                                sku.optionValues(),
                                                                sku.price(),
                                                                sku.initialStock()))
                                        .toList())));
    }

    /** 상품을 고친다. 상태와 상관없이 고칠 수 있고, 판매 중이면 검색에 몇 초 안에 반영된다. */
    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            Principal principal,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        update.execute(
                new UpdateProductCommand(
                        sellerId(principal),
                        productId,
                        request.categoryId(),
                        request.name(),
                        request.description(),
                        request.imageFileIds(),
                        request.skus().stream()
                                .map(
                                        sku ->
                                                new UpdateProductCommand.Sku(
                                                        sku.skuId(),
                                                        sku.optionValues(),
                                                        sku.price(),
                                                        Objects.requireNonNullElse(
                                                                sku.initialStock(), 0)))
                                .toList()));
    }

    /**
     * 판매를 시작한다. 이미 판매 중이면 204다. 이미지가 없거나 등록 중인 상품이 아니면 422 {@code CATALOG_PRODUCT_NOT_PUBLISHABLE}.
     */
    @PostMapping("/{productId}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(Principal principal, @PathVariable UUID productId) {
        publish.execute(new PublishProductCommand(sellerId(principal), productId));
    }

    /**
     * 판매를 멈춘다(CAT-2). 이미 멈췄으면 204. 등록 중이면 422 {@code CATALOG_INVALID_STATUS}, 제재된 상품이면 422 {@code
     * CATALOG_PRODUCT_BANNED}.
     */
    @PostMapping("/{productId}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(Principal principal, @PathVariable UUID productId) {
        stop.execute(new ChangeSaleStatusCommand(sellerId(principal), productId));
    }

    /**
     * 직접 멈춘 상품의 판매를 다시 연다. 이미 판매 중이면 204. 판매자 정지 중이면 422 {@code CATALOG_SELLER_SUSPENDED}, 제재된 상품이면
     * 422 {@code CATALOG_PRODUCT_BANNED}.
     */
    @PostMapping("/{productId}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(Principal principal, @PathVariable UUID productId) {
        resume.execute(new ChangeSaleStatusCommand(sellerId(principal), productId));
    }

    /**
     * SKU 실재고를 증감 수량만큼 바꾸고 이력을 남긴다(CAT-3). {@code Idempotency-Key}가 필요하다. 조정 뒤 실재고가 예약 수량보다 작거나
     * 999,999보다 크면 422 {@code CATALOG_STOCK_OUT_OF_RANGE}, 상품에 없는 SKU는 404 {@code
     * CATALOG_SKU_NOT_FOUND}.
     */
    @PostMapping("/{productId}/skus/{skuId}/stock-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public AdjustStockResponse adjustStock(
            Principal principal,
            @PathVariable UUID productId,
            @PathVariable UUID skuId,
            @Valid @RequestBody AdjustStockRequest request) {
        AdjustStockUseCase.Result result =
                adjustStock.execute(
                        new AdjustStockCommand(
                                sellerId(principal),
                                productId,
                                skuId,
                                request.delta(),
                                request.reason()));
        return new AdjustStockResponse(
                result.adjustmentId(), result.onHand(), result.reserved(), result.available());
    }
}
