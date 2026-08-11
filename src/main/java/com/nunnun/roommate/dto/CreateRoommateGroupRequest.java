package com.nunnun.roommate.dto; import jakarta.validation.constraints.*; public record CreateRoommateGroupRequest(@NotBlank @Size(max=50) String name){}
