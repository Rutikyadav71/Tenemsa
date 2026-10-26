package com.tenemsa.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public class LoginRequest {
    @NotBlank(message = "Email is required.") @Size(max = 255, message = "Email is too long.")
    public String email;
    @NotBlank(message = "Password is required.") @Size(max = 128, message = "Password is too long.")
    public String password;
    // Longer-lived refresh token when true (see TokenService); defaults to
    // false via primitive boolean, matching "unchecked" as the safe default
    // for a checkbox the frontend doesn't have to explicitly set.
    public boolean rememberMe;
    // Optional workspace slug chosen on the "find your workspace" step. When
    // present, the account must belong to that company or login is refused.
    @Size(max = 100, message = "Workspace is too long.")
    public String workspace;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}

