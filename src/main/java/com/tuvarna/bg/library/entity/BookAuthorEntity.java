package com.tuvarna.bg.library.entity;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Getter
@Setter
public class BookAuthorEntity {
    private BookEntity book;
    private AuthorEntity author;
}
