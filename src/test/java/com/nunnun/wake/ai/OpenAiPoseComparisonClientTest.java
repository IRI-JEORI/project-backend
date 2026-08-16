package com.nunnun.wake.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.global.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiPoseComparisonClientTest {

    private OpenAiPoseComparisonClient client;

    @BeforeEach
    void setUp() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setVisionModel("gpt-5-mini");
        client = new OpenAiPoseComparisonClient(properties, new ObjectMapper());
    }

    @Test
    void buildsResponsesRequestWithTwoHighDetailImagesAndStrictScoreSchema() {
        String request = client.createRequest(
                "https://signed.example/reference",
                "https://signed.example/submitted",
                "cross both arms"
        ).toString();

        assertThat(request)
                .contains("gpt-5-mini")
                .contains("https://signed.example/reference")
                .contains("https://signed.example/submitted")
                .contains("cross both arms")
                .contains("high")
                .contains("pose_similarity_score")
                .contains("minimum=0")
                .contains("maximum=100")
                .contains("strict=true")
                .contains("additionalProperties=false");
    }

    @Test
    void parsesValidScoreAndRejectsMalformedOrOutOfRangeOutput() {
        assertThat(client.parseScore("{\"score\":70}")).isEqualTo(70);
        assertThatThrownBy(() -> client.parseScore("not-json"))
                .isInstanceOf(PoseComparisonException.class);
        assertThatThrownBy(() -> client.parseScore("{\"score\":null}"))
                .isInstanceOf(PoseComparisonException.class);
        assertThatThrownBy(() -> client.parseScore("{\"score\":-1}"))
                .isInstanceOf(PoseComparisonException.class);
        assertThatThrownBy(() -> client.parseScore("{\"score\":101}"))
                .isInstanceOf(PoseComparisonException.class);
    }
}
