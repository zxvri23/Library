package com.tuvarna.bg.library.entity;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class BookGenreEntity {
    private BookEntity book;
    private BookGenreEntity genre;
}
