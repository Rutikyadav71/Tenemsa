package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Outlook calendar (Microsoft 365): upcoming events. Read only. */
@Component
public class MicrosoftCalendarProvider extends MicrosoftProvider {

    public MicrosoftCalendarProvider(ConnectorHttp http) { super(http); }

    @Override public String code() { return "microsoft_calendar"; }
    @Override public String label() { return "Outlook Calendar"; }
    @Override public String description() { return "Your upcoming Outlook meetings and events."; }
    @Override public String access() { return "Read your calendar events. Tenemsa never creates or changes events."; }
    @Override protected String scope() { return "Calendars.Read"; }

    private List<Item> events(String token, Instant from, Instant to, int top, String search, boolean full) {
        String url = "https://graph.microsoft.com/v1.0/me/calendarView?startDateTime=" + enc(from.toString()) + "&endDateTime=" + enc(to.toString())
                + "&$top=" + top + "&$orderby=" + enc("start/dateTime") + "&$select=" + enc("subject,start,end,location,webLink,isAllDay");
        List<Item> out = new ArrayList<>();
        for (JsonNode e : http.get(url, token).path("value")) {
            String title = e.path("subject").asText("(no title)");
            if (search != null && !title.toLowerCase().contains(search.toLowerCase())) continue;
            String start = e.path("start").path("dateTime").asText("") + " " + e.path("start").path("timeZone").asText("");
            if (!full) { out.add(new Item(title, start.strip(), e.path("webLink").asText(""))); continue; }
            String end = e.path("end").path("dateTime").asText("") + " " + e.path("end").path("timeZone").asText("");
            String where = e.path("location").path("displayName").asText("");
            out.add(new Item(title, "from " + start.strip() + " to " + end.strip() + (where.isBlank() ? "" : ", at " + where), e.path("webLink").asText("")));
        }
        return out;
    }

    @Override
    public List<Item> context(String token) { return context(token, QueryHints.NONE); }

    @Override
    public List<Item> context(String token, QueryHints hints) {
        Instant from = hints.from() != null ? hints.from() : Instant.now();
        Instant to = hints.to() != null ? hints.to() : from.plus(14, ChronoUnit.DAYS);
        return events(token, from, to, 40, hints.firstTerm(), true);
    }

    @Override
    public List<Item> preview(String token) {
        Instant now = Instant.now();
        return events(token, now, now.plus(14, ChronoUnit.DAYS), 8, null, false);
    }
}
