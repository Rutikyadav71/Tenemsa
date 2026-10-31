package com.tenemsa.platform.ai.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per AI call. It records WHO called, WHICH feature and prompt version, HOW LONG it took,
 * how many tokens were used and how it ended. It deliberately does NOT store the prompt or the answer,
 * so meeting notes are not copied into a log table. Used for quotas, the usage page and cost tracking.
 */
@Entity
@Table(name = "ai_request_logs", indexes = {
        @Index(name = "idx_ai_logs_company_created", columnList = "company_id, created_at"),
        @Index(name = "idx_ai_logs_company_emp_created", columnList = "company_id, emp_id, created_at")
})
public class AiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "emp_id")
    private Integer empId;

    @Column(nullable = false, length = 40)
    private String feature;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 40)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Integer getEmpId() { return empId; }
    public void setEmpId(Integer empId) { this.empId = empId; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
