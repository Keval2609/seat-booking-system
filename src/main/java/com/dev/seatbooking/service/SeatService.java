package com.dev.seatbooking.service;

import com.dev.seatbooking.dto.BulkSeatsRequest;
import com.dev.seatbooking.dto.BulkSeatsResponse;
import com.dev.seatbooking.dto.SeatResponse;
import com.dev.seatbooking.error.ApiException;
import com.dev.seatbooking.repository.EventRepository;
import com.dev.seatbooking.repository.SeatRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatService {
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public SeatService(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public BulkSeatsResponse bulkCreate(Long eventId, BulkSeatsRequest req) {
        requireEvent(eventId);
        if (seatRepository.existsByEventId(eventId)) {
            throw seatsExist();
        }
        try {
            int created = seatRepository.bulkInsert(eventId, req.rows(), req.columns(), req.priceCents());
            return new BulkSeatsResponse(created);
        } catch (DataIntegrityViolationException e) {
            throw seatsExist();
        }
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> list(Long eventId) {
        requireEvent(eventId);
        return seatRepository.findViewsByEventId(eventId);
    }

    private void requireEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found");
        }
    }

    private ApiException seatsExist() {
        return new ApiException(HttpStatus.CONFLICT, "SEATS_ALREADY_EXIST", "Seats already exist for this event");
    }
}
