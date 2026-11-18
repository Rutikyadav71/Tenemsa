package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** OneDrive (Microsoft 365): recently used files and file search. Read only. */
@Component
public class MicrosoftOneDriveProvider extends MicrosoftProvider {

    public MicrosoftOneDriveProvider(ConnectorHttp http) { super(http); }

    @Override public String code() { return "microsoft_onedrive"; }
    @Override public String label() { return "OneDrive"; }
    @Override public String description() { return "Your recently used OneDrive files."; }
    @Override public String access() { return "Read your files. Tenemsa never creates, edits or deletes anything in OneDrive."; }
    @Override protected String scope() { return "Files.Read"; }

    private List<Item> files(String token, String url, QueryHints hints, boolean full) {
        List<Item> out = new ArrayList<>();
        for (JsonNode f : http.get(url, token).path("value")) {
            String modified = f.path("lastModifiedDateTime").asText("");
            if (hints.from() != null && !modified.isEmpty() && modified.compareTo(hints.from().toString()) < 0) continue;
            if (hints.to() != null && !modified.isEmpty() && modified.compareTo(hints.to().toString()) >= 0) continue;
            out.add(new Item(f.path("name").asText(""), (full && f.has("file") ? f.path("file").path("mimeType").asText("") + ", " : "") + "changed " + modified, f.path("webUrl").asText("")));
        }
        return out;
    }

    @Override
    public List<Item> context(String token) { return context(token, QueryHints.NONE); }

    @Override
    public List<Item> context(String token, QueryHints hints) {
        String sel = "&$select=" + enc("name,file,lastModifiedDateTime,webUrl");
        String url = hints.firstTerm() != null
                // Graph's search takes the text inside quotes; a quote inside the text is doubled.
                ? "https://graph.microsoft.com/v1.0/me/drive/root/search(q='" + enc(hints.firstTerm().replace("'", "''")) + "')?$top=30" + sel
                : "https://graph.microsoft.com/v1.0/me/drive/recent?$top=" + (hints.isEmpty() ? 20 : 30) + sel;
        return files(token, url, hints, true);
    }

    @Override
    public List<Item> preview(String token) {
        return files(token, "https://graph.microsoft.com/v1.0/me/drive/recent?$top=8&$select=" + enc("name,lastModifiedDateTime,webUrl"), QueryHints.NONE, false);
    }
}
