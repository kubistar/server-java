package kr.hhplus.be.server.external.entity;

public enum FailedDataStatus {
    FAILED,      // 실패
    RETRYING,    // 재시도 중
    SUCCESS,     // 재시도 성공
    ABANDONED    // 재시도 포기
}