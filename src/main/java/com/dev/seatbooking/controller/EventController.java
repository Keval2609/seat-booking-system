package com.dev.seatbooking.controller;

import com.dev.seatbooking.dto.BulkSeatsRequest;
import com.dev.seatbooking.dto.BulkSeatsResponse;
import com.dev.seatbooking.dto.CreateEventRequest;
import com.dev.seatbooking.dto.EventResponse;
import com.dev.seatbooking.dto.PageResponse;
import com.dev.seatbooking.dto.SeatResponse;
import com.dev.seatbooking.service.EventService;
import com.dev.seatbooking.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;
    private final SeatService seatService;

    public EventController(EventService eventService, SeatService seatService) {
        this.eventService = eventService;
        this.seatService = seatService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        EventResponse created = eventService.create(req, Long.valueOf(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<EventResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return eventService.list(page, size);
    }

    @PostMapping("/{id}/seats/bulk")
    public ResponseEntity<BulkSeatsResponse> bulk(@PathVariable Long id,
                                                  @Valid @RequestBody BulkSeatsRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seatService.bulkCreate(id, req));
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> seats(@PathVariable Long id) {
        return seatService.list(id);
    }
}
