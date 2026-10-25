package com.winwin.authapi.process;

import com.winwin.authapi.common.ApiExceptions.DataApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** Thin HTTP client for data-api. Adds the shared X-Internal-Token header to every call. */
@Component
public class DataApiClient {

    private record TransformResponse(String result) {
    }

    private final RestClient client;

    public DataApiClient(@Value("${app.data-api-url}") String baseUrl,
                         @Value("${app.internal-token}") String internalToken) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    public String transform(String text) {
        try {
            TransformResponse response = client.post()
                    .uri("/api/transform")
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(TransformResponse.class);
            if (response == null || response.result() == null) {
                throw new DataApiException("empty response from data-api", null);
            }
            return response.result();
        } catch (RestClientException e) {
            // Covers connection refused, timeouts and non-2xx statuses (e.g. 403 on token mismatch).
            throw new DataApiException(e.getMessage(), e);
        }
    }
}
