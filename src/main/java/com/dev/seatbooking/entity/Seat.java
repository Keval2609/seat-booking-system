package com.dev.seatbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Objects;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "hold_id")
    private Long holdId;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Seat() {}

    public Seat(Long eventId, String label, int priceCents, SeatStatus status) {
        this.eventId = eventId;
        this.label = label;
        this.priceCents = priceCents;
        this.status = status;
    }

    public Seat(Long eventId, String label, int priceCents, SeatStatus status, Long holdId) {
        this.eventId = eventId;
        this.label = label;
        this.priceCents = priceCents;
        this.status = status;
        this.holdId = holdId;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getLabel() {
        return label;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public Long getHoldId() {
        return holdId;
    }

    public Long getVersion() {
        return version;
    }

    public void setHoldId(Long holdId) {
        this.holdId = holdId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seat seat = (Seat) o;
        return id != null && Objects.equals(id, seat.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
