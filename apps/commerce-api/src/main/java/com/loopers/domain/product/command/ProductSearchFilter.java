package com.loopers.domain.product.command;

import com.loopers.domain.product.enums.ProductSortCondition;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductSearchFilter {

    private final Long brandId;
    private final String keyword;
    private final ProductSortCondition sortCondition;

    public static ProductSearchFilter of(Long brandId, String keyword, ProductSortCondition sortCondition) {
        return ProductSearchFilter.builder()
                .brandId(brandId)
                .keyword(keyword)
                .sortCondition(sortCondition)
                .build();
    }
}