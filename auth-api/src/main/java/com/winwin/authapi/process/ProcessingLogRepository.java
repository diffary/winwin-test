package com.winwin.authapi.process;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessingLogRepository extends JpaRepository<ProcessingLog, UUID> {
}
