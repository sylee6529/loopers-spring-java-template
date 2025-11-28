package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.like.service.LikeReadService;
import com.loopers.domain.product.command.ProductSearchFilter;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.product.service.ProductReadService;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductReadServiceTest {

    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private BrandRepository brandRepository;
    
    @Mock
    private LikeReadService likeReadService;

    private ProductReadService productReadService;

    @BeforeEach
    void setUp() {
        productReadService = new ProductReadService(productRepository, brandRepository, likeReadService);
    }

    @DisplayName("상품 상세 조회")
    @Nested
    class GetProductDetail {

        @DisplayName("정상적인 상품 상세 조회가 성공한다")
        @Test
        void shouldGetProductDetail_whenValidInput() {
            // given
            Long productId = 1L;
            String memberId = "member1";
            Long brandId = 1L;

            Product product = createProduct(brandId);
            Brand brand = createBrand();

            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(likeReadService.isLikedBy(memberId, productId)).willReturn(true);

            // when
            ProductDetailInfo result = productReadService.getProductDetail(productId, memberId);

            // then
            assertThat(result.getId()).isEqualTo(product.getId());
            assertThat(result.getName()).isEqualTo(product.getName());
            assertThat(result.getDescription()).isEqualTo(product.getDescription());
            assertThat(result.getBrandName()).isEqualTo(brand.getName());
            assertThat(result.getBrandDescription()).isEqualTo(brand.getDescription());
            assertThat(result.getPrice()).isEqualTo(product.getPrice());
            assertThat(result.getStock()).isEqualTo(product.getStock());
            assertThat(result.getLikeCount()).isEqualTo(product.getLikeCount());
            assertThat(result.isLikedByMember()).isTrue();

            verify(productRepository).findById(productId);
            verify(brandRepository).findById(brandId);
            verify(likeReadService).isLikedBy(memberId, productId);
        }

        @DisplayName("회원 ID가 null인 경우에도 조회가 성공한다")
        @Test
        void shouldGetProductDetail_whenMemberIdIsNull() {
            // given
            Long productId = 1L;
            String memberId = null;
            Long brandId = 1L;

            Product product = createProduct(brandId);
            Brand brand = createBrand();

            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(likeReadService.isLikedBy(memberId, productId)).willReturn(false);

            // when
            ProductDetailInfo result = productReadService.getProductDetail(productId, memberId);

            // then
            assertThat(result.getId()).isEqualTo(product.getId());
            assertThat(result.getName()).isEqualTo(product.getName());
            assertThat(result.getDescription()).isEqualTo(product.getDescription());
            assertThat(result.getBrandName()).isEqualTo(brand.getName());
            assertThat(result.getBrandDescription()).isEqualTo(brand.getDescription());
            assertThat(result.getPrice()).isEqualTo(product.getPrice());
            assertThat(result.getStock()).isEqualTo(product.getStock());
            assertThat(result.getLikeCount()).isEqualTo(product.getLikeCount());
            assertThat(result.isLikedByMember()).isFalse();

            verify(productRepository).findById(productId);
            verify(brandRepository).findById(brandId);
            verify(likeReadService).isLikedBy(memberId, productId);
        }

        @DisplayName("존재하지 않는 상품 조회 시 예외가 발생한다")
        @Test
        void shouldThrowException_whenProductNotFound() {
            // given
            Long productId = 999L;
            String memberId = "member1";

            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productReadService.getProductDetail(productId, memberId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");

            verify(productRepository).findById(productId);
            verify(brandRepository, never()).findById(any());
            verify(likeReadService, never()).isLikedBy(any(), any());
        }

        @DisplayName("존재하지 않는 브랜드 조회 시 예외가 발생한다")
        @Test
        void shouldThrowException_whenBrandNotFound() {
            // given
            Long productId = 1L;
            String memberId = "member1";
            Long brandId = 999L;

            Product product = createProduct(brandId);

            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productReadService.getProductDetail(productId, memberId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드를 찾을 수 없습니다");

            verify(productRepository).findById(productId);
            verify(brandRepository).findById(brandId);
            verify(likeReadService, never()).isLikedBy(any(), any());
        }
    }

    @DisplayName("상품 목록 조회")
    @Nested
    class GetProducts {

        @DisplayName("정상적인 상품 목록 조회가 성공한다")
        @Test
        void shouldGetProducts_whenValidInput() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 10);
            String memberId = "member1";

            Product product1 = createProduct(1L);
            Product product2 = createProduct(2L);
            Brand brand1 = createBrand(1L);
            Brand brand2 = createBrand(2L);

            Page<Product> productPage = new PageImpl<>(List.of(product1, product2));

            given(productRepository.findAll(filter, pageable)).willReturn(productPage);
            given(brandRepository.findByIdIn(List.of(1L, 2L))).willReturn(List.of(brand1, brand2));
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of(product1.getId()));

            // when
            Page<ProductSummaryInfo> result = productReadService.getProducts(filter, pageable, memberId);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.getContent().get(0).getName()).isEqualTo(product1.getName());
            assertThat(result.getContent().get(0).getBrandName()).isEqualTo(brand1.getName());
            assertThat(result.getContent().get(0).getPrice()).isEqualTo(product1.getPrice());
            assertThat(result.getContent().get(0).getLikeCount()).isEqualTo(product1.getLikeCount());
            assertThat(result.getContent().get(0).isLikedByMember()).isTrue();
            assertThat(result.getContent().get(1).getId()).isEqualTo(product2.getId());
            assertThat(result.getContent().get(1).getName()).isEqualTo(product2.getName());
            assertThat(result.getContent().get(1).getBrandName()).isEqualTo(brand2.getName());
            assertThat(result.getContent().get(1).getPrice()).isEqualTo(product2.getPrice());
            assertThat(result.getContent().get(1).getLikeCount()).isEqualTo(product2.getLikeCount());
            assertThat(result.getContent().get(1).isLikedByMember()).isFalse();

            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository).findByIdIn(any());
            verify(likeReadService).findLikedProductIds(eq(memberId), any());
        }

        @DisplayName("brandId로 필터링하여 조회가 성공한다")
        @Test
        void shouldGetProducts_whenBrandIdProvided() {
            // given
            Long brandId = 1L;
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(brandId)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);
            String memberId = "member1";

            Product product1 = createProduct(brandId);
            Product product2 = createProduct(brandId);
            Brand brand = createBrand(brandId);

            Page<Product> productPage = new PageImpl<>(List.of(product1, product2));

            given(productRepository.findAll(filter, pageable)).willReturn(productPage);
            given(brandRepository.findByIdIn(List.of(brandId))).willReturn(List.of(brand));
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of());

            // when
            Page<ProductSummaryInfo> result = productReadService.getProducts(filter, pageable, memberId);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).allMatch(info -> info.getBrandName().equals(brand.getName()));

            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository).findByIdIn(List.of(brandId));
        }

        @DisplayName("brandId와 keyword를 함께 사용하여 조회가 성공한다")
        @Test
        void shouldGetProducts_whenBrandIdAndKeywordProvided() {
            // given
            Long brandId = 1L;
            String keyword = "테스트";
            ProductSearchFilter filter = ProductSearchFilter.builder()
                    .brandId(brandId)
                    .keyword(keyword)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);
            String memberId = "member1";

            Product product = createProduct(brandId);
            Brand brand = createBrand(brandId);

            Page<Product> productPage = new PageImpl<>(List.of(product));

            given(productRepository.findAll(filter, pageable)).willReturn(productPage);
            given(brandRepository.findByIdIn(List.of(brandId))).willReturn(List.of(brand));
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of());

            // when
            Page<ProductSummaryInfo> result = productReadService.getProducts(filter, pageable, memberId);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getBrandName()).isEqualTo(brand.getName());

            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository).findByIdIn(List.of(brandId));
        }

        @DisplayName("회원 ID가 null인 경우에도 목록 조회가 성공한다")
        @Test
        void shouldGetProducts_whenMemberIdIsNull() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 10);
            String memberId = null;

            Product product = createProduct(1L);
            Brand brand = createBrand();
            Page<Product> productPage = new PageImpl<>(List.of(product));

            given(productRepository.findAll(filter, pageable)).willReturn(productPage);
            given(brandRepository.findByIdIn(List.of(1L))).willReturn(List.of(brand));

            // when
            Page<ProductSummaryInfo> result = productReadService.getProducts(filter, pageable, memberId);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).isLikedByMember()).isFalse();

            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository).findByIdIn(List.of(1L));
            verify(likeReadService, never()).findLikedProductIds(any(), any());
        }

        @DisplayName("상품에 해당하는 브랜드가 없으면 예외가 발생한다")
        @Test
        void shouldThrowException_whenBrandNotFound() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 10);
            String memberId = "member1";

            Product product = createProduct(999L);
            Page<Product> productPage = new PageImpl<>(List.of(product));

            given(productRepository.findAll(filter, pageable)).willReturn(productPage);
            given(brandRepository.findByIdIn(List.of(999L))).willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> productReadService.getProducts(filter, pageable, memberId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드를 찾을 수 없습니다");

            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository).findByIdIn(any());
        }

        @DisplayName("빈 상품 목록이 반환될 때도 정상 동작한다")
        @Test
        void shouldReturnEmptyPage_whenNoProductsFound() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 10);
            String memberId = "member1";

            Page<Product> emptyPage = new PageImpl<>(List.of());

            given(productRepository.findAll(filter, pageable)).willReturn(emptyPage);

            // when
            Page<ProductSummaryInfo> result = productReadService.getProducts(filter, pageable, memberId);

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository, never()).findById(any());
            verify(likeReadService, never()).isLikedBy(any(), any());
        }
    }

    @DisplayName("인기 상품 TOP100 조회")
    @Nested
    class GetPopularProducts {

        @DisplayName("인기 상품 TOP100 조회가 성공한다")
        @Test
        void shouldGetPopularProducts_whenValidInput() {
            // given
            String memberId = "member1";

            // 좋아요 수가 다른 여러 상품 생성
            Product product1 = createProductWithLikeCount(1L, 100);
            Product product2 = createProductWithLikeCount(2L, 80);
            Product product3 = createProductWithLikeCount(3L, 60);

            Brand brand1 = createBrand(1L);
            Brand brand2 = createBrand(2L);
            Brand brand3 = createBrand(3L);

            List<Product> products = List.of(product1, product2, product3);

            given(productRepository.findTopByLikeCount(100)).willReturn(products);
            given(brandRepository.findByIdIn(List.of(1L, 2L, 3L))).willReturn(List.of(brand1, brand2, brand3));
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of(product1.getId()));

            // when
            List<ProductSummaryInfo> result = productReadService.getPopularProducts(memberId);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.get(0).getLikeCount()).isEqualTo(100);
            assertThat(result.get(0).isLikedByMember()).isTrue();

            assertThat(result.get(1).getId()).isEqualTo(product2.getId());
            assertThat(result.get(1).getLikeCount()).isEqualTo(80);
            assertThat(result.get(1).isLikedByMember()).isFalse();

            assertThat(result.get(2).getId()).isEqualTo(product3.getId());
            assertThat(result.get(2).getLikeCount()).isEqualTo(60);
            assertThat(result.get(2).isLikedByMember()).isFalse();

            verify(productRepository).findTopByLikeCount(100);
            verify(brandRepository).findByIdIn(any());
            verify(likeReadService).findLikedProductIds(eq(memberId), any());
        }

        @DisplayName("회원 ID가 null인 경우에도 인기 상품 조회가 성공한다")
        @Test
        void shouldGetPopularProducts_whenMemberIdIsNull() {
            // given
            String memberId = null;

            Product product1 = createProductWithLikeCount(1L, 100);
            Product product2 = createProductWithLikeCount(2L, 80);

            Brand brand1 = createBrand(1L);
            Brand brand2 = createBrand(2L);

            List<Product> products = List.of(product1, product2);

            given(productRepository.findTopByLikeCount(100)).willReturn(products);
            given(brandRepository.findByIdIn(List.of(1L, 2L))).willReturn(List.of(brand1, brand2));

            // when
            List<ProductSummaryInfo> result = productReadService.getPopularProducts(memberId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).isLikedByMember()).isFalse();
            assertThat(result.get(1).isLikedByMember()).isFalse();

            verify(productRepository).findTopByLikeCount(100);
            verify(brandRepository).findByIdIn(any());
            verify(likeReadService, never()).findLikedProductIds(any(), any());
        }

        @DisplayName("상품이 100개 미만이면 전체를 반환한다")
        @Test
        void shouldReturnAllProducts_whenLessThan100Products() {
            // given
            String memberId = "member1";

            Product product1 = createProductWithLikeCount(1L, 50);
            Product product2 = createProductWithLikeCount(2L, 30);

            Brand brand1 = createBrand(1L);
            Brand brand2 = createBrand(2L);

            List<Product> products = List.of(product1, product2);

            given(productRepository.findTopByLikeCount(100)).willReturn(products);
            given(brandRepository.findByIdIn(List.of(1L, 2L))).willReturn(List.of(brand1, brand2));
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of());

            // when
            List<ProductSummaryInfo> result = productReadService.getPopularProducts(memberId);

            // then
            assertThat(result).hasSize(2);

            verify(productRepository).findTopByLikeCount(100);
        }

        @DisplayName("빈 목록이 반환될 때도 정상 동작한다")
        @Test
        void shouldReturnEmptyList_whenNoProducts() {
            // given
            String memberId = "member1";

            given(productRepository.findTopByLikeCount(100)).willReturn(List.of());

            // when
            List<ProductSummaryInfo> result = productReadService.getPopularProducts(memberId);

            // then
            assertThat(result).isEmpty();

            verify(productRepository).findTopByLikeCount(100);
            verify(brandRepository, never()).findByIdIn(any());
            verify(likeReadService, never()).findLikedProductIds(any(), any());
        }
    }

    @DisplayName("브랜드별 인기 상품 TOP N 조회")
    @Nested
    class GetBrandPopularProducts {

        @DisplayName("특정 브랜드의 인기 상품 TOP N 조회가 성공한다")
        @Test
        void shouldGetBrandPopularProducts_whenValidInput() {
            // given
            Long brandId = 1L;
            String memberId = "member1";
            int limit = 5;

            Product product1 = createProductWithLikeCount(1L, 100);
            Product product2 = createProductWithLikeCount(2L, 80);
            Product product3 = createProductWithLikeCount(3L, 60);

            Brand brand = createBrand(brandId);

            List<Product> products = List.of(product1, product2, product3);

            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(productRepository.findTopByBrandIdAndLikeCount(brandId, limit)).willReturn(products);
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of(product1.getId()));

            // when
            List<ProductSummaryInfo> result = productReadService.getBrandPopularProducts(brandId, limit, memberId);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.get(0).getLikeCount()).isEqualTo(100);
            assertThat(result.get(0).isLikedByMember()).isTrue();

            assertThat(result.get(1).getId()).isEqualTo(product2.getId());
            assertThat(result.get(1).getLikeCount()).isEqualTo(80);
            assertThat(result.get(1).isLikedByMember()).isFalse();

            verify(brandRepository).findById(brandId);
            verify(productRepository).findTopByBrandIdAndLikeCount(brandId, limit);
            verify(likeReadService).findLikedProductIds(eq(memberId), any());
        }

        @DisplayName("회원 ID가 null인 경우에도 브랜드별 인기 상품 조회가 성공한다")
        @Test
        void shouldGetBrandPopularProducts_whenMemberIdIsNull() {
            // given
            Long brandId = 1L;
            String memberId = null;
            int limit = 10;

            Product product1 = createProductWithLikeCount(1L, 100);
            Product product2 = createProductWithLikeCount(2L, 80);

            Brand brand = createBrand(brandId);

            List<Product> products = List.of(product1, product2);

            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(productRepository.findTopByBrandIdAndLikeCount(brandId, limit)).willReturn(products);

            // when
            List<ProductSummaryInfo> result = productReadService.getBrandPopularProducts(brandId, limit, memberId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).isLikedByMember()).isFalse();
            assertThat(result.get(1).isLikedByMember()).isFalse();

            verify(brandRepository).findById(brandId);
            verify(productRepository).findTopByBrandIdAndLikeCount(brandId, limit);
            verify(likeReadService, never()).findLikedProductIds(any(), any());
        }

        @DisplayName("해당 브랜드의 상품이 없으면 빈 리스트를 반환한다")
        @Test
        void shouldReturnEmptyList_whenNoProducts() {
            // given
            Long brandId = 1L;
            String memberId = "member1";
            int limit = 10;

            Brand brand = createBrand(brandId);

            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(productRepository.findTopByBrandIdAndLikeCount(brandId, limit)).willReturn(List.of());

            // when
            List<ProductSummaryInfo> result = productReadService.getBrandPopularProducts(brandId, limit, memberId);

            // then
            assertThat(result).isEmpty();

            verify(brandRepository).findById(brandId);
            verify(productRepository).findTopByBrandIdAndLikeCount(brandId, limit);
            verify(likeReadService, never()).findLikedProductIds(any(), any());
        }

        @DisplayName("존재하지 않는 브랜드로 조회 시 예외가 발생한다")
        @Test
        void shouldThrowException_whenBrandNotFound() {
            // given
            Long brandId = 999L;
            String memberId = "member1";
            int limit = 10;

            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productReadService.getBrandPopularProducts(brandId, limit, memberId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드를 찾을 수 없습니다");

            verify(brandRepository).findById(brandId);
            verify(productRepository, never()).findTopByBrandIdAndLikeCount(any(), anyInt());
            verify(likeReadService, never()).findLikedProductIds(any(), any());
        }
    }

    @DisplayName("협력 객체 사용")
    @Nested
    class Collaboration {

        @DisplayName("모든 협력 객체가 올바르게 호출된다")
        @Test
        void shouldCallAllCollaborators_whenGetProductDetail() {
            // given
            Long productId = 1L;
            String memberId = "member1";
            Long brandId = 1L;

            Product product = createProduct(brandId);
            Brand brand = createBrand();

            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(likeReadService.isLikedBy(memberId, productId)).willReturn(true);

            // when
            productReadService.getProductDetail(productId, memberId);

            // then
            verify(productRepository).findById(productId);
            verify(brandRepository).findById(brandId);
            verify(likeReadService).isLikedBy(memberId, productId);
        }

        @DisplayName("페이징된 조회에서 모든 상품과 브랜드가 조회된다")
        @Test
        void shouldCallRepositoryForAllProducts_whenGetProducts() {
            // given
            ProductSearchFilter filter = ProductSearchFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 2);
            String memberId = "member1";

            Product product1 = createProduct(1L);
            Product product2 = createProduct(2L);
            Page<Product> productPage = new PageImpl<>(List.of(product1, product2));

            Brand brand1 = createBrand(1L);
            Brand brand2 = createBrand(2L);

            given(productRepository.findAll(filter, pageable)).willReturn(productPage);
            given(brandRepository.findByIdIn(List.of(1L, 2L))).willReturn(List.of(brand1, brand2));
            given(likeReadService.findLikedProductIds(eq(memberId), any())).willReturn(Set.of());

            // when
            productReadService.getProducts(filter, pageable, memberId);

            // then
            verify(productRepository).findAll(filter, pageable);
            verify(brandRepository).findByIdIn(any());
            verify(likeReadService).findLikedProductIds(eq(memberId), any());
        }
    }

    private Product createProduct(Long brandId) {
        Product product = new Product(
                brandId,
                "테스트 상품",
                "상품 설명",
                Money.of(10000),
                Stock.of(100)
        );

        // 리플렉션을 사용해서 ID를 설정
        try {
            java.lang.reflect.Field idField = product.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(product, brandId); // Use brandId as productId for testing
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return product;
    }

    private Brand createBrand() {
        return createBrand(1L);
    }

    private Brand createBrand(Long id) {
        Brand brand = new Brand("테스트 브랜드", "브랜드 설명");

        // 리플렉션을 사용해서 ID를 설정
        try {
            java.lang.reflect.Field idField = brand.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(brand, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return brand;
    }

    private Product createProductWithLikeCount(Long productId, int likeCount) {
        Product product = new Product(
                productId,
                "테스트 상품 " + productId,
                "상품 설명",
                Money.of(10000),
                Stock.of(100)
        );

        // 리플렉션을 사용해서 ID와 likeCount를 설정
        try {
            java.lang.reflect.Field idField = product.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(product, productId);

            java.lang.reflect.Field likeCountField = product.getClass().getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.set(product, likeCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return product;
    }
}
