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
    public List<Item> context(String token) {
        String url = "https://www.googleapis.com/drive/v3/files?pageSize=20&orderBy=" + enc("modifiedTime desc") + "&fields=" + enc("files(id,name,mimeType,modifiedTime)");
        List<Item> out = new ArrayList<>();
        for (JsonNode f : http.get(url, token).path("files")) {
            out.add(new Item(f.path("name").asText(""), f.path("mimeType").asText("") + ", changed " + f.path("modifiedTime").asText(""), ""));
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
