package com.tuvarna.bg.library.entity;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReservationEntity {
    @   EqualsAndHashCode.Include
    private Integer reservationsId;
    private UserEntity user;
    private BookEntity book;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String status;

    public ReservationEntity(UserEntity user, BookEntity book) {
        this.user = user;
        this.book = book;
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return "PENDING".equals(status) || "READY".equals(status);
    }

    @Override
    public String toString() {
        return "Reservation #" + reservationsId + " - " + book.getTitle() + " by " + user.getFullName();
    }
}
