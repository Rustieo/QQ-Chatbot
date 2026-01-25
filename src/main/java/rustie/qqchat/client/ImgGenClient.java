package rustie.qqchat.client;

import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
@Component
@RequiredArgsConstructor
public class ImgGenClient {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper;
    private final String endpoint=" https://yinli.one/v1/chat/completions";
    private final String token="sk-66n4m53bKXuLx3VCSTtAQULBWtc978lAFpS312uEuiRyZTJX";

    public String generateImageBase64(String prompt) throws IOException {
        String jsonBody = """
        {
          "model": "gemini-2.5-flash-image-preview",
          "stream": false,
          "messages": [
            {"role": "user", "content": %s}
          ]
        }
        """.formatted(mapper.writeValueAsString(prompt));

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response resp = okHttpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                String err = resp.body() != null ? resp.body().string() : "";
                throw new IOException("Image API failed: HTTP " + resp.code() + " " + err);
            }

            String body = resp.body() != null ? resp.body().string() : "";
            JsonNode root = mapper.readTree(body);

            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new IOException("Image API response missing choices[0].message.content");
            }

            String content = contentNode.asText();
            if (content == null || content.isBlank()) {
                throw new IOException("Image API returned empty base64 content");
            }
            return content;
        }
    }
}
