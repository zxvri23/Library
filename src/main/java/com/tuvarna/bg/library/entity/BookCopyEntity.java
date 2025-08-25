package com.tuvarna.bg.library.entity;

import lombok.*;

import java.awt.print.Book;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BookCopyEntity {
        @EqualsAndHashCode.Include
        private Integer copiesId;
        private BookEntity book;
        private String status;
        private LocalDate acquiredAt;
        private List<LoanEntity> loans;

        public BookCopyEntity(BookEntity book, String status, LocalDate acquiredAt) {
            this.book = book;
            this.status = status;
            this.acquiredAt = acquiredAt;
        }

        @Override
        public String toString() {
            return "Copy #" + copiesId + " - " + book.getTitle() + " (" + status + ")";
        }
}
