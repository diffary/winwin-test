package com.winwin.authapi.process;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/process")
public class ProcessController {

    record ProcessRequest(@NotBlank String text) {
    }

    record ProcessResponse(String result) {
    }

    private final DataApiClient dataApi;
    private final ProcessingLogRepository logs;

    public ProcessController(DataApiClient dataApi, ProcessingLogRepository logs) {
        this.dataApi = dataApi;
        this.logs = logs;
    }

    @PostMapping
    public ProcessResponse process(@Valid @RequestBody ProcessRequest request,
                                   @AuthenticationPrincipal UUID userId) {
        String result = dataApi.transform(request.text());
        logs.save(new ProcessingLog(userId, request.text(), result));
        return new ProcessResponse(result);
    }
}
