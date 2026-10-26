package com.tenemsa.platform.dto;

/** What an invitee sees before choosing a password. No ids, no token. */
public record InvitationPreview(String companyName, String companySlug, String email, String name, String role) {}
