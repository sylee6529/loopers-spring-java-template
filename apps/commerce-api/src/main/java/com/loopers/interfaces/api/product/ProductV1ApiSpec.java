package com.loopers.interfaces.api.product;

import com.loopers.domain.product.enums.ProductSortCondition;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Product V1", description = "상품 관리 API")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회 (오프셋)", description = "검색 조건에 따라 상품 목록을 조회합니다 (오프셋 기반 페이지네이션).")
    ApiResponse<Page<ProductV1Dto.ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) @Parameter(description = "브랜드 ID") Long brandId,
            @RequestParam(required = false) @Parameter(description = "검색 키워드") String keyword,
            @RequestParam(required = false, defaultValue = "LATEST") @Parameter(description = "정렬 조건") ProductSortCondition sort,
            @RequestParam(defaultValue = "0") @Min(0) @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) @Parameter(description = "페이지 크기") int size,
            @RequestHeader(value = "X-USER-ID", required = false) @Parameter(description = "회원 ID") Long memberId
    );

    @Operation(summary = "상품 목록 조회 (커서)", description = "검색 조건에 따라 상품 목록을 조회합니다 (커서 기반 페이지네이션).")
    ApiResponse<ProductV1Dto.CursorPageResponse<ProductV1Dto.ProductSummaryResponse>> getProductsByCursor(
            @RequestParam(required = false) @Parameter(description = "브랜드 ID") Long brandId,
            @RequestParam(required = false) @Parameter(description = "검색 키워드") String keyword,
            @RequestParam(required = false, defaultValue = "LATEST") @Parameter(description = "정렬 조건") ProductSortCondition sort,
            @RequestParam(required = false) @Parameter(description = "커서 (다음 페이지 조회용)") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) @Parameter(description = "페이지 크기") int size,
            @RequestHeader(value = "X-USER-ID", required = false) @Parameter(description = "회원 ID") Long memberId
    );

    @Operation(summary = "상품 상세 조회", description = "특정 상품의 상세 정보를 조회합니다.")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
            @PathVariable Long productId,
            @RequestHeader(value = "X-USER-ID", required = false) Long memberId
    );

    @Operation(summary = "인기 상품 TOP100 조회", description = "좋아요 수 기준 상위 100개 인기 상품을 조회합니다.")
    ApiResponse<java.util.List<ProductV1Dto.ProductSummaryResponse>> getPopularProducts(
            @RequestHeader(value = "X-USER-ID", required = false) @Parameter(description = "회원 ID") Long memberId
    );

    @Operation(summary = "브랜드별 인기 상품 TOP N 조회", description = "특정 브랜드의 인기 상품을 좋아요 수 기준으로 조회합니다.")
    ApiResponse<java.util.List<ProductV1Dto.ProductSummaryResponse>> getBrandPopularProducts(
            @PathVariable @Parameter(description = "브랜드 ID") Long brandId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) @Parameter(description = "조회 개수 (기본 10, 최대 100)") int limit,
            @RequestHeader(value = "X-USER-ID", required = false) @Parameter(description = "회원 ID") Long memberId
    );
}