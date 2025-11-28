package com.loopers.support.seed;

import com.github.javafaker.Faker;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.like.Like;
import com.loopers.domain.members.Member;
import com.loopers.domain.members.enums.Gender;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Stock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("local") // local 프로필에서만 활성화
@RequiredArgsConstructor
public class LocalDataSeeder implements CommandLineRunner {

    @PersistenceContext
    private final EntityManager em;

    private final Faker faker = new Faker(Locale.KOREAN);
    private final Random random = new Random();

    private static final String[] PREFIXES = {
            "프리미엄", "고급", "실용적인", "모던", "클래식", "스마트", "편리한", "특별한",
            "럭셔리", "인기", "신상", "트렌디", "감각적인", "세련된", "유니크", "심플",
            "미니멀", "빈티지", "레트로", "컴팩트", "슬림", "와이드", "퍼펙트", "베스트",
            "스페셜", "익스클루시브", "리미티드", "에디션", "올뉴", "뉴", "플러스", "프로",
            "울트라", "맥스", "라이트", "헤비", "소프트", "하드", "쿨", "핫", "프레시", "드라이",
            "웜", "콜드", "딥", "샬로우", "롱", "쇼트", "빅", "스몰", "미디엄", "라지",
            "익스트라", "수퍼", "하이퍼", "메가", "기가", "터보", "파워", "스트롱", "위크", "마일드",
            "하이", "로우", "미들", "탑", "베이직", "어드밴스드", "프로페셔널", "아마추어", "마스터", "엑스퍼트",
            "초경량", "경량", "중량", "초소형", "소형", "중형", "대형", "초대형", "휴대용", "고정형",
            "무선", "유선", "블루투스", "와이파이", "스마트폰용", "태블릿용", "PC용", "맥용", "윈도우용", "안드로이드용",
            "올시즌", "사계절", "봄", "여름", "가을", "겨울", "데일리", "주말", "출퇴근", "여행",
            "캠핑", "등산", "러닝", "피트니스", "요가", "수영", "사이클", "골프", "테니스", "축구",
            "남성", "여성", "유니섹스", "키즈", "아동", "주니어", "시니어", "성인", "패밀리", "커플"
    };

    private static final String[] MIDDLES = {
            "면", "코튼", "폴리", "나일론", "울", "실크", "가죽", "인조가죽", "스웨이드", "데님",
            "메탈", "플라스틱", "우드", "글라스", "세라믹", "실리콘", "고무", "스테인리스", "알루미늄", "티타늄",
            "카본", "크롬", "구리", "황동", "청동", "은", "금", "다이아몬드", "크리스탈", "펄",
            "화이트", "블랙", "그레이", "레드", "블루", "그린", "옐로우", "오렌지", "핑크", "퍼플",
            "네이비", "베이지", "브라운", "카키", "민트", "라벤더", "스카이", "로즈", "와인", "올리브",
            "라운드", "스퀘어", "오벌", "하트", "스타", "트라이앵글", "헥사곤", "다이아", "크로스", "서클",
            "스트라이프", "체크", "도트", "플라워", "애니멀", "지오메트릭", "그래픽", "레터링", "로고", "플레인",
            "매트", "글로시", "메탈릭", "펄", "글리터", "홀로그램", "네온", "파스텔", "비비드", "뉴트럴",
            "슬림핏", "레귤러핏", "오버핏", "루즈핏", "와이드핏", "크롭", "롱", "미디", "숏", "미니",
            "접이식", "분리형", "일체형", "모듈형", "확장형", "고정식", "회전형", "슬라이드", "폴딩", "스택"
    };

    private static final String[] ITEMS = {
            "노트북", "의자", "책상", "키보드", "마우스", "모니터", "가방", "시계",
            "신발", "티셔츠", "바지", "자켓", "스니커즈", "지갑", "벨트", "모자",
            "선글라스", "이어폰", "스피커", "카메라", "텀블러", "우산", "수첩", "볼펜",
            "향수", "립스틱", "크림", "샴푸", "비누", "칫솔", "램프", "쿠션", "담요",
            "베개", "매트", "컵", "접시", "냄비", "프라이팬", "수저", "젓가락", "도마",
            "칼", "가위", "테이프", "풀", "자", "계산기", "충전기", "케이블", "패드",
            "헤드셋", "웹캠", "마이크", "스탠드", "거치대", "케이스", "파우치", "홀더", "클립", "핀",
            "스티커", "메모지", "노트", "다이어리", "플래너", "캘린더", "포스터", "액자", "앨범", "파일",
            "폴더", "바인더", "클리어파일", "인덱스", "북마크", "책갈피", "펜슬", "샤프", "지우개", "형광펜",
            "마커", "색연필", "크레파스", "물감", "붓", "팔레트", "캔버스", "스케치북", "화판", "이젤",
            "가습기", "공기청정기", "선풍기", "히터", "온열기", "냉풍기", "제습기", "가열기", "토스터", "블렌더",
            "믹서", "커피머신", "정수기", "전기포트", "전기밥솥", "압력솥", "에어프라이어", "오븐", "전자레인지", "인덕션",
            "식기세척기", "세탁기", "건조기", "청소기", "로봇청소기", "스팀청소기", "다리미", "스티머", "재봉틀", "미싱",
            "드라이버", "렌치", "펜치", "니퍼", "와이어커터", "톱", "망치", "못", "나사", "볼트"
    };

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Long productCount = em.createQuery("SELECT COUNT(p) FROM Product p", Long.class)
                .getSingleResult();

        if (productCount != null && productCount > 0) {
            System.out.println("[LocalDataSeeder] products already exist, skipping seeding.");
            return;
        }

        int memberCount = 1000;
        int brandCount = 50;
        int totalProducts = 100_000;
        int likesCount = 50_000;

        createMembers(memberCount);
        em.flush();
        em.clear();

        createBrands(brandCount);
        em.flush();
        em.clear();

        createProducts(brandCount, totalProducts);
        em.flush();
        em.clear();

        createLikes(memberCount, totalProducts, likesCount);
        em.flush();
        em.clear();

        System.out.println("[LocalDataSeeder] Seeding completed.");
    }

    private void createMembers(int memberCount) {
        Gender[] genders = Gender.values();
        for (int i = 0; i < memberCount; i++) {
            String memberId = "user" + String.format("%06d", i);  // user000000 ~ user000999
            String email = memberId + "@example.com";
            String password = "password123";
            LocalDate birthDate = LocalDate.of(
                    1970 + random.nextInt(40),  // 1970~2009
                    1 + random.nextInt(12),      // 1~12월
                    1 + random.nextInt(28)       // 1~28일
            );
            Gender gender = genders[random.nextInt(genders.length)];

            Member member = new Member(memberId, email, password, birthDate.toString(), gender);

            em.persist(member);

            if (i % 100 == 0 && i > 0) {
                em.flush();
                em.clear();
            }
        }
        em.flush();
        em.clear();
        System.out.println("[LocalDataSeeder] Inserted " + memberCount + " members");
    }

    private void createBrands(int brandCount) {
        String[] brandNames = {
                "삼성", "LG", "애플", "나이키", "아디다스", "자라", "유니클로", "무인양품",
                "이케아", "다이소", "올리브영", "랄라블라", "스타벅스", "투썸플레이스", "이디야",
                "파리바게뜨", "뚜레주르", "던킨도너츠", "배스킨라빈스", "하겐다즈", "CU", "GS25",
                "세븐일레븐", "이마트", "롯데마트", "홈플러스", "코스트코", "현대백화점", "롯데백화점", "신세계백화점",
                "쿠팡", "마켓컬리", "SSG", "11번가", "옥션", "G마켓", "위메프", "티몬",
                "카카오", "네이버", "라인", "쿠팡이츠", "배달의민족", "요기요", "카카오택시", "타다",
                "에어비앤비", "야놀자", "여기어때", "인터파크"
        };

        for (int i = 0; i < brandCount; i++) {
            String name = brandNames[i % brandNames.length];
            if (i >= brandNames.length) {
                name = name + (i / brandNames.length + 1);  // 중복 방지
            }
            String description = faker.lorem().sentence();
            Brand brand = new Brand(name, description);

            em.persist(brand);

            if (i % 100 == 0 && i > 0) {
                em.flush();
                em.clear();
            }
        }
        em.flush();
        em.clear();
        System.out.println("[LocalDataSeeder] Inserted " + brandCount + " brands");
    }

    private void createProducts(int brandCount, int totalProducts) {
        for (int i = 0; i < totalProducts; i++) {
            long brandId = random.nextInt(brandCount) + 1L; // 1~brandCount

            // 랜덤하게 2단어 또는 3단어 조합
            String name;
            if (random.nextBoolean()) {
                // PREFIX + ITEM (2단어)
                name = PREFIXES[random.nextInt(PREFIXES.length)] + " " +
                       ITEMS[random.nextInt(ITEMS.length)];
            } else {
                // PREFIX + MIDDLE + ITEM (3단어)
                name = PREFIXES[random.nextInt(PREFIXES.length)] + " " +
                       MIDDLES[random.nextInt(MIDDLES.length)] + " " +
                       ITEMS[random.nextInt(ITEMS.length)];
            }
            String description = faker.lorem().sentence();
            BigDecimal priceValue = BigDecimal.valueOf(1_000 + random.nextInt(1_000_000));
            int stockQty = random.nextInt(1000);

            Money price = Money.of(priceValue);
            Stock stock = Stock.of(stockQty);

            Product product = new Product(
                    brandId,
                    name,
                    description,
                    price,
                    stock
            );

            em.persist(product);

            if (i % 1000 == 0 && i > 0) {
                em.flush();
                em.clear();
                System.out.println("[LocalDataSeeder] Inserted products: " + i);
            }
        }

        em.flush();
        em.clear();
        System.out.println("[LocalDataSeeder] Inserted " + totalProducts + " products");
    }

    private void createLikes(int memberCount, int totalProducts, int likesCount) {
        // Member의 PK를 조회 (1부터 memberCount까지)
        Set<String> uniqueLikes = new HashSet<>();
        int created = 0;

        while (created < likesCount) {
            long memberPkId = random.nextInt(memberCount) + 1L;  // Member PK는 1부터 시작
            long productId = random.nextInt(totalProducts) + 1L;

            String key = memberPkId + ":" + productId;

            if (uniqueLikes.add(key)) {
                Like like = new Like(memberPkId, productId);
                em.persist(like);

                // Product의 likeCount 증가 (JPQL 업데이트)
                em.createQuery("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId")
                        .setParameter("productId", productId)
                        .executeUpdate();

                created++;

                if (created % 1000 == 0) {
                    em.flush();
                    em.clear();
                    System.out.println("[LocalDataSeeder] Inserted likes: " + created);
                }
            }
        }
        em.flush();
        em.clear();
        System.out.println("[LocalDataSeeder] Inserted " + likesCount + " likes");
    }

}
