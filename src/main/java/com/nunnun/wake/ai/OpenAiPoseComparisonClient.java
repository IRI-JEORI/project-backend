package com.nunnun.wake.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.global.config.OpenAiProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseTextConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiPoseComparisonClient implements PoseComparisonClient {

    private static final String INSTRUCTIONS = """
            You are evaluating whether the person in the submitted image performs the requested
            body pose. Do not compare visual similarity with a reference artwork. Judge only body
            geometry and pose. Treat a horizontally mirrored pose as equivalent because users may
            use a mirrored selfie camera.

            Evaluate shoulder position, upper-arm direction, elbow position, forearm direction,
            hand position relative to the body, torso orientation, hip position, knee bend, leg
            position, and overall body configuration.

            Ignore completely identity, face, facial expression, clothing, hair, skin tone, body
            shape, background, lighting, camera quality, camera distance, image style, and
            animation versus photograph. Do not perform identity recognition. A real human
            correctly performing the described pose should receive a high score even if their
            appearance is completely different from the reference artwork. Never raise the score
            merely because the person or image looks identical.

            Score only how well the target pose requirements are satisfied:
            - 90-100: Core joints and body direction are nearly exact.
            - 75-89: The core pose is correct with only small angle or position differences.
            - 50-74: The pose intent is visible, but some important joints differ.
            - 20-49: Only some pose elements are similar.
            - 0-19: The pose is different or a person's pose cannot be verified.

            Return an integer score from 0 to 100.
            """;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiPoseComparisonClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public int compare(String submittedImageUrl, String poseDescription) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new PoseComparisonException("OpenAI API key is not configured.");
        }
        try {
            String output = createClient().responses().create(
                            createRequest(submittedImageUrl, poseDescription))
                    .output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(responseMessage -> responseMessage.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new PoseComparisonException("OpenAI returned no pose score."));
            return parseScore(output);
        } catch (PoseComparisonException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PoseComparisonException("OpenAI pose comparison failed.", exception);
        }
    }

    ResponseCreateParams createRequest(String submittedImageUrl, String poseDescription) {
        ResponseInputItem.Message message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addContent(ResponseInputText.builder().text(prompt(poseDescription)).build())
                .addContent(image(submittedImageUrl))
                .build();
        return ResponseCreateParams.builder()
                .model(properties.getVisionModel())
                .instructions(INSTRUCTIONS)
                .inputOfResponse(List.of(ResponseInputItem.ofMessage(message)))
                .text(ResponseTextConfig.builder().format(scoreFormat()).build())
                .build();
    }

    int parseScore(String output) {
        try {
            Integer score = objectMapper.readValue(output, PoseScoreOutput.class).score;
            if (score == null || score < 0 || score > 100) {
                throw new PoseComparisonException("OpenAI returned an invalid pose score.");
            }
            return score;
        } catch (PoseComparisonException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PoseComparisonException("OpenAI returned malformed structured output.", exception);
        }
    }

    private ResponseInputImage image(String imageUrl) {
        return ResponseInputImage.builder()
                .detail(ResponseInputImage.Detail.HIGH)
                .imageUrl(imageUrl)
                .build();
    }

    private String prompt(String poseDescription) {
        if (!StringUtils.hasText(poseDescription)) {
            return "Target pose requirements are unavailable. Evaluate only the body geometry "
                    + "visible in the submitted image and return a conservative score.";
        }
        return "Target pose requirements (the primary scoring criteria):\n"
                + poseDescription + "\n\n"
                + "Evaluate only whether the person in the submitted image satisfies these "
                + "requirements.";
    }

    private ResponseFormatTextJsonSchemaConfig scoreFormat() {
        Map<String, Object> score = Map.of(
                "type", "integer",
                "minimum", 0,
                "maximum", 100
        );
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("score", score),
                "required", List.of("score"),
                "additionalProperties", false
        );
        return ResponseFormatTextJsonSchemaConfig.builder()
                .name("pose_similarity_score")
                .strict(true)
                .schema(ResponseFormatTextJsonSchemaConfig.Schema.builder()
                        .putAllAdditionalProperties(schema.entrySet().stream().collect(
                                java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))
                        ))
                        .build())
                .build();
    }

    private OpenAIClient createClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(properties.getApiKey())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(0)
                .build();
    }

    public static class PoseScoreOutput {
        public Integer score;
    }
}
