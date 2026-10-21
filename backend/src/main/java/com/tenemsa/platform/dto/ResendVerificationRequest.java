package com.tenemsa.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResendVerificationRequest {
    @NotBlank(message = "Email is required.") @Size(max = 255, message = "Email is too long.")
    private String email;
    @Size(max = 100, message = "Workspace is too long.")
    private String workspace;
    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
