package com.winwin.authapi.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_log")
public class ProcessingLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "input_text", nullable = false)
    private String inputText;

    @Column(name = "output_text", nullable = false)
    private String outputText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessingLog() {
        // required by JPA
    }

    public ProcessingLog(UUID userId, String inputText, String outputText) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.inputText = inputText;
        this.outputText = outputText;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }
}
