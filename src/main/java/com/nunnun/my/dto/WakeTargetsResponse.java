package com.nunnun.my.dto;

import java.util.List;

public record WakeTargetsResponse(
        List<WakeTargetListItemResponse> targets
) {
}
