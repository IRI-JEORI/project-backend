package com.nunnun.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."),
    FIXED_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "FIXED_SCHEDULE_NOT_FOUND", "Fixed schedule not found."),
    INVALID_FIXED_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "INVALID_FIXED_SCHEDULE_TIME", "Start time must be before end time."),
    SLEEP_FEEDBACK_ALREADY_EXISTS(HttpStatus.CONFLICT, "SLEEP_FEEDBACK_ALREADY_EXISTS", "Sleep feedback already exists for today."),
    WAKE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "WAKE_GROUP_NOT_FOUND", "Wake group not found."),
    WAKE_GROUP_ALREADY_JOINED(HttpStatus.CONFLICT, "WAKE_GROUP_ALREADY_JOINED", "User already joined this wake group."),
    WAKE_GROUP_FULL(HttpStatus.CONFLICT, "WAKE_GROUP_FULL", "Wake group is full."),
    WAKE_GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "WAKE_GROUP_MEMBER_NOT_FOUND", "Wake group member not found."),
    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_CODE_GENERATION_FAILED", "Failed to generate invite code."),
    INVALID_TIMETABLE_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_TIMETABLE_IMAGE", "Invalid timetable image."),
    SCHEDULE_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "SCHEDULE_ANALYSIS_FAILED", "Schedule analysis failed."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN", "Expired refresh token."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials."),
    INVALID_JWT(HttpStatus.UNAUTHORIZED, "INVALID_JWT", "Invalid token."),
    EXPIRED_JWT(HttpStatus.UNAUTHORIZED, "EXPIRED_JWT", "Expired token."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "이미 존재하는 데이터입니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", "비즈니스 규칙을 위반했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
