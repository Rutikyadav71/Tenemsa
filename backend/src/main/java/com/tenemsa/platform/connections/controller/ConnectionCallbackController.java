package com.tenemsa.platform.connections.controller;

import com.tenemsa.platform.connections.service.ConnectionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Where an app sends the browser after the person signs in. Open to the internet; the signed state is the lock. */
@RestController
public class ConnectionCallbackController {

    private final ConnectionService service;

    public ConnectionCallbackController(ConnectionService service) { this.service = service; }

    @GetMapping("/api/connections/callback/{provider}")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String next = service.finishSignIn(provider, code, state, error);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, URI.create(next).toString()).header("Cache-Control", "no-store").build();
    }
}
