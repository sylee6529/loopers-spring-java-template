package com.loopers.domain.payment;

public enum PaymentStatus {
    /**
     * 결제 진행 중 (PG 처리 대기)
     */
    PENDING,

    /**
     * 결제 성공
     */
    SUCCESS,

    /**
     * 결제 실패 (한도초과, 잘못된 카드 등)
     */
    FAILED,

    /**
     * 결제 취소 (사용자/시스템에 의해 중단)
     */
    CANCELLED
}
