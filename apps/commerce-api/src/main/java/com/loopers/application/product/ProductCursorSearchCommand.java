package com.loopers.application.product;

import com.loopers.domain.product.enums.ProductSortCondition;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductCursorSearchCommand {

    private final Long brandId;
    private final String keyword;
    private final ProductSortCondition sort;
    private final String cursor;
    private final int size;
    private final Long memberIdOrNull;

    public static ProductCursorSearchCommand of(Long brandId, String keyword, ProductSortCondition sort, String cursor, int size, Long memberIdOrNull) {
        return ProductCursorSearchCommand.builder()
                .brandId(brandId)
                .keyword(keyword)
                .sort(sort)
                .cursor(cursor)
                .size(size)
                .memberIdOrNull(memberIdOrNull)
                .build();
    }
}
