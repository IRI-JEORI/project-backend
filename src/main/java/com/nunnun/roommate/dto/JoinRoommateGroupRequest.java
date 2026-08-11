package com.nunnun.roommate.dto; import jakarta.validation.constraints.*; public record JoinRoommateGroupRequest(@NotBlank @Size(max=20) String inviteCode){}
