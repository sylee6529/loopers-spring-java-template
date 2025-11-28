package com.loopers.infrastructure.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.command.ProductSearchFilter;
import com.loopers.domain.product.enums.ProductSortCondition;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.vo.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRepositoryImplTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand brand1;
    private Brand brand2;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        // 브랜드 생성
        brand1 = brandRepository.save(new Brand("Nike", "Nike Brand"));
        brand2 = brandRepository.save(new Brand("Adidas", "Adidas Brand"));

        // 상품 생성
        product1 = productRepository.save(new Product(
                brand1.getId(),
                "Nike Shoes",
                "Running shoes",
                Money.of(BigDecimal.valueOf(100000)),
                Stock.of(50)
        ));

        product2 = productRepository.save(new Product(
                brand1.getId(),
                "Nike Shirt",
                "Sports shirt",
                Money.of(BigDecimal.valueOf(50000)),
                Stock.of(100)
        ));

        product3 = productRepository.save(new Product(
                brand2.getId(),
                "Adidas Shoes",
                "Football shoes",
                Money.of(BigDecimal.valueOf(120000)),
                Stock.of(30)
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("brandId 기반 상품 검색")
    @Nested
    class SearchByBrandId {

        @DisplayName("brandId로 필터링하면 해당 브랜드 상품만 조회된다")
        @Test
        void shouldReturnProductsOfSpecificBrand_whenBrandIdProvided() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(brand1.getId())
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.findAll(filter, pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::getBrandId)
                    .containsOnly(brand1.getId());
            assertThat(result.getContent()).extracting(Product::getName)
                    .containsExactlyInAnyOrder("Nike Shoes", "Nike Shirt");
        }

        @DisplayName("존재하지 않는 brandId로 조회하면 빈 결과를 반환한다")
        @Test
        void shouldReturnEmptyResult_whenBrandIdNotExists() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(999L)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.findAll(filter, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }

        @DisplayName("brandId와 keyword를 함께 사용하면 두 조건 모두 만족하는 상품만 조회된다")
        @Test
        void shouldReturnProducts_whenBothBrandIdAndKeywordProvided() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(brand1.getId())
                    .keyword("Shoes")
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.findAll(filter, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Nike Shoes");
            assertThat(result.getContent().get(0).getBrandId()).isEqualTo(brand1.getId());
        }

        @DisplayName("brandId와 정렬 조건을 함께 사용할 수 있다")
        @Test
        void shouldApplySorting_whenBrandIdAndSortConditionProvided() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(brand1.getId())
                    .sortCondition(ProductSortCondition.PRICE_ASC)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.findAll(filter, pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Nike Shirt"); // 50000원
            assertThat(result.getContent().get(1).getName()).isEqualTo("Nike Shoes"); // 100000원
        }

        @DisplayName("brandId 없이 조회하면 모든 브랜드 상품이 조회된다")
        @Test
        void shouldReturnAllProducts_whenBrandIdNotProvided() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.findAll(filter, pageable);

            // then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent()).extracting(Product::getBrandId)
                    .containsExactlyInAnyOrder(brand1.getId(), brand1.getId(), brand2.getId());
        }

        @DisplayName("brandId가 null이면 모든 브랜드 상품이 조회된다")
        @Test
        void shouldReturnAllProducts_whenBrandIdIsNull() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(null)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.findAll(filter, pageable);

            // then
            assertThat(result.getContent()).hasSize(3);
        }
    }
}
