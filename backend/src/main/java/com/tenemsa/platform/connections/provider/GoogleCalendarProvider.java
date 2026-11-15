package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Google Calendar: upcoming events on the person's main calendar. Read only. */
@Component
public class GoogleCalendarProvider extends GoogleProvider {

    public GoogleCalendarProvider(ConnectorHttp http) { super(http); }

    @Override public String code() { return "google_calendar"; }
    @Override public String label() { return "Google Calendar"; }
    @Override public String description() { return "Your upcoming meetings and events."; }
    @Override public String access() { return "Read your calendar events. Tenemsa never creates or changes events."; }
    @Override protected String scope() { return "https://www.googleapis.com/auth/calendar.readonly"; }

    @Override
    public List<Item> context(String token) {
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=25&singleEvents=true&orderBy=startTime&timeMin=" + enc(Instant.now().toString());
        List<Item> out = new ArrayList<>();
        for (JsonNode e : http.get(url, token).path("items")) {
            String start = e.path("start").path("dateTime").asText(e.path("start").path("date").asText(""));
            String end = e.path("end").path("dateTime").asText(e.path("end").path("date").asText(""));
            String where = e.path("location").asText("");
            out.add(new Item(e.path("summary").asText("(no title)"), "from " + start + " to " + end + (where.isBlank() ? "" : ", at " + where), ""));
        }
        return out;
    }

    @Override
    public List<Item> preview(String token) {
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=8&singleEvents=true&orderBy=startTime&timeMin=" + enc(Instant.now().toString());
        List<Item> out = new ArrayList<>();
        for (JsonNode e : http.get(url, token).path("items")) {
            String when = e.path("start").path("dateTime").asText(e.path("start").path("date").asText(""));
            out.add(new Item(e.path("summary").asText("(no title)"), when, e.path("htmlLink").asText("")));
        }
        return out;
    }
}
