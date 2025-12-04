package com.loopers.interfaces.api.product;

import com.loopers.application.product.*;
import com.loopers.domain.product.enums.ProductSortCondition;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping("/products")
    public ApiResponse<Page<ProductV1Dto.ProductSummaryResponse>> getProducts(
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false, defaultValue = "LATEST") ProductSortCondition sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-USER-ID", required = false) Long memberId
    ) {
        ProductSearchCommand command = ProductSearchCommand.of(brandId, keyword, sort, page, size, memberId);
        Page<ProductSummaryInfo> products = productFacade.getProducts(command);

        Page<ProductV1Dto.ProductSummaryResponse> response = products.map(ProductV1Dto.ProductSummaryResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/products/cursor")
    public ApiResponse<ProductV1Dto.CursorPageResponse<ProductV1Dto.ProductSummaryResponse>> getProductsByCursor(
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false, defaultValue = "LATEST") ProductSortCondition sort,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-USER-ID", required = false) Long memberId
    ) {
        ProductCursorSearchCommand command = ProductCursorSearchCommand.of(brandId, keyword, sort, cursor, size, memberId);
        CursorPageInfo<ProductSummaryInfo> products = productFacade.getProductsByCursor(command);

        java.util.List<ProductV1Dto.ProductSummaryResponse> content = products.getContent().stream()
                .map(ProductV1Dto.ProductSummaryResponse::from)
                .toList();

        ProductV1Dto.CursorPageResponse<ProductV1Dto.ProductSummaryResponse> response =
                ProductV1Dto.CursorPageResponse.of(content, products.getNextCursor(), products.isHasNext());

        return ApiResponse.success(response);
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
            @PathVariable(value = "productId") Long productId,
            @RequestHeader(value = "X-USER-ID", required = false) Long memberId
    ) {
        ProductDetailInfo productDetail = productFacade.getProductDetail(productId, memberId);
        ProductV1Dto.ProductDetailResponse response = ProductV1Dto.ProductDetailResponse.from(productDetail);
        return ApiResponse.success(response);
    }

    @GetMapping("/products/popular")
    public ApiResponse<java.util.List<ProductV1Dto.ProductSummaryResponse>> getPopularProducts(
            @RequestHeader(value = "X-USER-ID", required = false) Long memberId
    ) {
        java.util.List<ProductSummaryInfo> popularProducts = productFacade.getPopularProducts(memberId);
        java.util.List<ProductV1Dto.ProductSummaryResponse> response = popularProducts.stream()
                .map(ProductV1Dto.ProductSummaryResponse::from)
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/brands/{brandId}/products/popular")
    public ApiResponse<java.util.List<ProductV1Dto.ProductSummaryResponse>> getBrandPopularProducts(
            @PathVariable(value = "brandId") Long brandId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestHeader(value = "X-USER-ID", required = false) Long memberId
    ) {
        java.util.List<ProductSummaryInfo> popularProducts = productFacade.getBrandPopularProducts(brandId, limit, memberId);
        java.util.List<ProductV1Dto.ProductSummaryResponse> response = popularProducts.stream()
                .map(ProductV1Dto.ProductSummaryResponse::from)
                .toList();
        return ApiResponse.success(response);
    }
}