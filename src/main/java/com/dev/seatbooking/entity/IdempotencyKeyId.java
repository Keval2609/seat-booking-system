package com.dev.seatbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotencyKeyId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false, length = 100)
    private String idemKey;

    protected IdempotencyKeyId() {}

    public IdempotencyKeyId(Long userId, String idemKey) {
        this.userId = userId;
        this.idemKey = idemKey;
    }

    public Long getUserId() {
        return userId;
    }

    public String getIdemKey() {
        return idemKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKeyId that = (IdempotencyKeyId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(idemKey, that.idemKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, idemKey);
    }
}
