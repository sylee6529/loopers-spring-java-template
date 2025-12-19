package com.loopers.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface EventHandledRepository extends JpaRepository<EventHandled, String> {

    /**
     * 이벤트 처리 여부 확인 (멱등성 체크)
     */
    boolean existsById(String eventId);

    /**
     * 특정 기간 이전의 처리 완료 이벤트 삭제 (정리용)
     * @param before 기준 시각
     */
    @Query("DELETE FROM EventHandled e WHERE e.handledAt < :before")
    void deleteOldEvents(@Param("before") ZonedDateTime before);

    /**
     * 특정 파티션 키의 처리 이력 조회 (디버깅용)
     */
    @Query("SELECT e FROM EventHandled e WHERE e.partitionKey = :partitionKey ORDER BY e.handledAt DESC")
    java.util.List<EventHandled> findByPartitionKey(@Param("partitionKey") String partitionKey);
}
