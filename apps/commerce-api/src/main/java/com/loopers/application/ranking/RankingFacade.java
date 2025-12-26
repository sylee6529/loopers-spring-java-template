package com.loopers.application.ranking;

import com.loopers.application.ranking.RankingInfo.RankingItemInfo;
import com.loopers.application.ranking.RankingInfo.RankingPageInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.infrastructure.cache.ProductRankingCache;
import com.loopers.infrastructure.cache.ProductRankingCache.RankingEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class RankingFacade {

    private final ProductRankingCache productRankingCache;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    /**
     * 랭킹 페이지 조회
     * @param date 날짜 (yyyyMMdd), null이면 오늘
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     * @return 랭킹 페이지 정보
     */
    public RankingPageInfo getRankings(String date, int page, int size) {
        // 날짜 기본값: 오늘
        String targetDate = date != null ? date : productRankingCache.getTodayDate();

        // 1. ZSET에서 랭킹 조회
        List<RankingEntry> rankingEntries = productRankingCache.getTopRankings(targetDate, page, size);

        if (rankingEntries.isEmpty()) {
            return RankingPageInfo.of(Collections.emptyList(), targetDate, page, size, 0);
        }

        // 2. 상품 ID 목록 추출
        List<Long> productIds = rankingEntries.stream()
                .map(RankingEntry::productId)
                .toList();

        // 3. 상품 정보 조회
        List<Product> products = productRepository.findByIdIn(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 4. 브랜드 정보 조회 (N+1 방지)
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();
        List<Brand> brands = brandRepository.findByIdIn(brandIds);
        Map<Long, Brand> brandMap = brands.stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        // 5. 응답 생성
        List<RankingItemInfo> rankings = rankingEntries.stream()
                .map(entry -> {
                    Product product = productMap.get(entry.productId());
                    if (product == null) {
                        log.warn("[Ranking] Product not found - productId: {}", entry.productId());
                        return null;
                    }
                    Brand brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getName() : "Unknown";

                    return new RankingItemInfo(
                            entry.rank(),
                            product.getId(),
                            product.getName(),
                            brandName,
                            product.getPrice(),
                            product.getLikeCount(),
                            entry.score()
                    );
                })
                .filter(item -> item != null)
                .toList();

        // 6. 전체 개수 조회
        long totalCount = productRankingCache.getTotalCount(targetDate);

        return RankingPageInfo.of(rankings, targetDate, page, size, totalCount);
    }
}
