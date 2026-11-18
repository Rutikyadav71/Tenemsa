package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Google Drive: recently changed files. Read only. */
@Component
public class GoogleDriveProvider extends GoogleProvider {

    public GoogleDriveProvider(ConnectorHttp http) { super(http); }

    @Override public String code() { return "google_drive"; }
    @Override public String label() { return "Google Drive"; }
    @Override public String description() { return "Your recently changed files and documents."; }
    @Override public String access() { return "Read your files. Tenemsa never creates, edits or deletes anything in Drive."; }
    @Override protected String scope() { return "https://www.googleapis.com/auth/drive.readonly"; }

    @Override
    public List<Item> context(String token) { return context(token, QueryHints.NONE); }

    @Override
    public List<Item> context(String token, QueryHints hints) {
        List<String> conditions = new ArrayList<>();
        if (hints.firstTerm() != null) {
            String t = hints.firstTerm().replace("\\", "\\\\").replace("'", "\\'");
            conditions.add("(name contains '" + t + "' or fullText contains '" + t + "')");
        }
        if (hints.from() != null) conditions.add("modifiedTime >= '" + hints.from() + "'");
        if (hints.to() != null) conditions.add("modifiedTime < '" + hints.to() + "'");
        if (!conditions.isEmpty()) conditions.add("trashed = false");
        String url = "https://www.googleapis.com/drive/v3/files?pageSize=" + (hints.isEmpty() ? 20 : 30) + "&orderBy=" + enc("modifiedTime desc")
                + (conditions.isEmpty() ? "" : "&q=" + enc(String.join(" and ", conditions)))
                + "&fields=" + enc("files(id,name,mimeType,modifiedTime,webViewLink)");
        List<Item> out = new ArrayList<>();
        for (JsonNode f : http.get(url, token).path("files")) {
            out.add(new Item(f.path("name").asText(""), f.path("mimeType").asText("") + ", changed " + f.path("modifiedTime").asText(""), f.path("webViewLink").asText("")));
        }
        return out;
    }

    @Override
    public List<Item> preview(String token) {
        String url = "https://www.googleapis.com/drive/v3/files?pageSize=8&orderBy=" + enc("modifiedTime desc") + "&fields=" + enc("files(id,name,modifiedTime,webViewLink)");
        List<Item> out = new ArrayList<>();
        for (JsonNode f : http.get(url, token).path("files")) {
            out.add(new Item(f.path("name").asText(""), "Changed " + f.path("modifiedTime").asText(""), f.path("webViewLink").asText("")));
        }
        return out;
    }
}
