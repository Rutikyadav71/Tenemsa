package com.tenemsa.platform.dto;


public class LoginRequest {
    public String email;
    public String password;
    // Longer-lived refresh token when true (see TokenService); defaults to
    // false via primitive boolean, matching "unchecked" as the safe default
    // for a checkbox the frontend doesn't have to explicitly set.
    public boolean rememberMe;

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

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}

