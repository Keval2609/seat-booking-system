package com.dev.seatbooking.service;

import com.dev.seatbooking.dto.CreateEventRequest;
import com.dev.seatbooking.dto.EventResponse;
import com.dev.seatbooking.dto.PageResponse;
import com.dev.seatbooking.entity.Event;
import com.dev.seatbooking.repository.EventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Note: If a signed token's subject has no matching user row, the created_by foreign key
     * constraint will fail with DataIntegrityViolationException (500). This cannot happen in v1
     * because users cannot be deleted and tokens are issued only for existing users.
     */
    @Transactional
    public EventResponse create(CreateEventRequest req, Long adminId) {
        Event saved = eventRepository.save(
                new Event(req.name(), req.venue(), req.startsAt(), adminId));
        return EventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> list(int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("startsAt").ascending().and(Sort.by("id")));
        return PageResponse.of(eventRepository.findAll(pr).map(EventResponse::from));
    }
}
