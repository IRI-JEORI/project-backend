package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ShareWakeProofRequest(
        @JsonProperty("group_ids") @NotEmpty List<@NotNull Long> groupIds
) {
}
