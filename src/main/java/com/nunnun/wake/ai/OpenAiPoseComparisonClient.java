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
            Compare the body pose in the first (reference) image with the body pose in the second
            (submitted) image. Evaluate pose similarity only.

            Consider hand and arm position, leg position, torso orientation, body direction, and
            overall body configuration. Ignore identity, face, facial features, clothing, hair,
            background, lighting, image style, and whether the reference is an illustration,
            animation, cosplay, or photo. Treat a horizontally mirrored pose as equivalent because
            users may use a mirrored selfie camera. Do not perform identity recognition.

            Return an integer pose similarity score from 0 to 100.
            """;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiPoseComparisonClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public int compare(String referenceImageUrl, String submittedImageUrl, String poseDescription) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new PoseComparisonException("OpenAI API key is not configured.");
        }
        try {
            String output = createClient().responses().create(
                            createRequest(referenceImageUrl, submittedImageUrl, poseDescription))
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

    ResponseCreateParams createRequest(
            String referenceImageUrl,
            String submittedImageUrl,
            String poseDescription
    ) {
        ResponseInputItem.Message message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addContent(ResponseInputText.builder().text(prompt(poseDescription)).build())
                .addContent(image(referenceImageUrl))
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
            return "The first image is the reference pose. The second image is the submitted pose.";
        }
        return "The first image is the reference pose. The second image is the submitted pose.\n"
                + "Reference pose description: " + poseDescription;
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
