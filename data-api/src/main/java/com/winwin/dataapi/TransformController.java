package com.winwin.dataapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transform")
public class TransformController {

    record TransformRequest(@NotBlank String text) {}

    record TransformResponse(String result) {}

    @PostMapping
    public TransformResponse transform(@Valid @RequestBody TransformRequest request) {
        String reversed = new StringBuilder(request.text()).reverse().toString();
        return new TransformResponse(reversed.toUpperCase());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onInvalidRequest() {
        return Map.of("error", "text must not be blank");
    }
}
