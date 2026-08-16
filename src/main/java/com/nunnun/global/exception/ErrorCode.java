package com.nunnun.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."),
    DEMO_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEMO_ACCOUNT_NOT_FOUND", "Demo account not found."),
    FIXED_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "FIXED_SCHEDULE_NOT_FOUND", "Fixed schedule not found."),
    INVALID_FIXED_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "INVALID_FIXED_SCHEDULE_TIME", "Start time must be before end time."),
    INVALID_WAKE_TARGET_FORMAT(
            HttpStatus.BAD_REQUEST,
            "INVALID_WAKE_TARGET_FORMAT",
            "예시와 같은 형식으로 다시 입력해주세요. 예) 월요일, 07:30"
    ),
    INVALID_DND_FORMAT(
            HttpStatus.BAD_REQUEST,
            "INVALID_DND_FORMAT",
            "예시와 같은 형식으로 다시 입력해주세요. 예) 월요일, 08:00~11:00"
    ),
    INVALID_TIME_RANGE(
            HttpStatus.BAD_REQUEST,
            "INVALID_TIME_RANGE",
            "시작 시간은 종료 시간보다 빨라야 합니다."
    ),
    WAKE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "WAKE_TARGET_NOT_FOUND", "Wake target not found."),
    SLEEP_FEEDBACK_ALREADY_EXISTS(HttpStatus.CONFLICT, "SLEEP_FEEDBACK_ALREADY_EXISTS", "Sleep feedback already exists for today."),
    WAKE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "WAKE_GROUP_NOT_FOUND", "Wake group not found."),
    WAKE_GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "WAKE_GROUP_ACCESS_DENIED", "No access to this wake group."),
    ACTIVE_WAKE_GROUP_EXISTS(HttpStatus.CONFLICT, "ACTIVE_WAKE_GROUP_EXISTS", "User already belongs to a wake group."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "ALREADY_MEMBER", "User already joined this wake group."),
    WAKE_GROUP_ALREADY_JOINED(HttpStatus.CONFLICT, "WAKE_GROUP_ALREADY_JOINED", "User already joined this wake group."),
    WAKE_GROUP_FULL(HttpStatus.CONFLICT, "WAKE_GROUP_FULL", "Wake group is full."),
    WAKE_GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "WAKE_GROUP_MEMBER_NOT_FOUND", "Wake group member not found."),
    ACTIVE_POSE_NOT_FOUND(HttpStatus.NOT_FOUND, "ACTIVE_POSE_NOT_FOUND", "Active pose not found."),
    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_CODE_GENERATION_FAILED", "Failed to generate invite code."),
    INVITE_CODE_EXPIRED(HttpStatus.CONFLICT, "INVITE_CODE_EXPIRED", "Invite code has expired."),
    WAKE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "WAKE_REQUEST_NOT_FOUND", "Wake request not found."),
    CANNOT_WAKE_SELF(HttpStatus.BAD_REQUEST, "CANNOT_WAKE_SELF", "Cannot wake yourself."),
    WAKE_GROUP_SENDER_NOT_MEMBER(HttpStatus.FORBIDDEN, "WAKE_GROUP_SENDER_NOT_MEMBER", "Sender is not a wake group member."),
    WAKE_GROUP_RECEIVER_NOT_MEMBER(HttpStatus.BAD_REQUEST, "WAKE_GROUP_RECEIVER_NOT_MEMBER", "Receiver is not a wake group member."),
    WAKE_BLOCKED_DND(HttpStatus.CONFLICT, "WAKE_BLOCKED_DND", "Receiver is in DND."),
    WAKE_COOLDOWN_ACTIVE(HttpStatus.CONFLICT, "WAKE_COOLDOWN_ACTIVE", "Receiver is in wake cooldown."),
    WAKE_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "WAKE_REQUEST_ACCESS_DENIED", "No access to this wake request."),
    WAKE_PROOF_ALREADY_EXISTS(HttpStatus.CONFLICT, "WAKE_PROOF_ALREADY_EXISTS", "Wake proof already exists."),
    INVALID_WAKE_PROOF_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_WAKE_PROOF_IMAGE", "Invalid wake proof image."),
    WAKE_PROOF_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "WAKE_PROOF_UPLOAD_FAILED", "Wake proof upload failed."),
    INVALID_WAKE_REQUEST_STATUS(HttpStatus.CONFLICT, "INVALID_WAKE_REQUEST_STATUS", "Wake request status does not allow this operation."),
    ROOMMATE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOMMATE_GROUP_NOT_FOUND", "Roommate group not found."),
    ROOMMATE_GROUP_ALREADY_EXISTS(HttpStatus.CONFLICT, "ROOMMATE_GROUP_ALREADY_EXISTS", "User already belongs to a roommate group."),
    ROOMMATE_GROUP_FULL(HttpStatus.CONFLICT, "ROOMMATE_GROUP_FULL", "Roommate group is full."),
    ROOMMATE_GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOMMATE_GROUP_MEMBER_NOT_FOUND", "Roommate group member not found."),
    ROOMMATE_NOT_AVAILABLE(HttpStatus.CONFLICT, "ROOMMATE_NOT_AVAILABLE", "An active roommate is required."),
    ROOMMATE_COMPLAINT_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOMMATE_COMPLAINT_NOT_FOUND", "Roommate complaint not found."),
    BEHAVIOR_MANUAL_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "BEHAVIOR_MANUAL_GENERATION_FAILED", "Failed to generate behavior manual."),
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
