package com.tuvarna.bg.library.entity;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LoanEntity {
    @EqualsAndHashCode.Include
    private Integer loansId;
    private UserEntity user;
    private UserEntity staff;
    private BookCopyEntity copy;
    private LocalDateTime borrowedAt;
    private LocalDate dueDate;
    private LocalDateTime returnedAt;

    public LoanEntity(UserEntity user, UserEntity staff, BookCopyEntity copy, LocalDate dueDate) {
        this.user = user;
        this.staff = staff;
        this.copy = copy;
        this.dueDate = dueDate;
        this.borrowedAt = LocalDateTime.now();
    }

    public boolean isOverdue() {
        return returnedAt == null && LocalDate.now().isAfter(dueDate);
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    @Override
    public String toString() {
        return "Loan #" + loansId + " - " + copy.getBook().getTitle() + " to " + user.getFullName();
    }
}
