package com.tuvarna.bg.library.entity;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BookEntity {

    @EqualsAndHashCode.Include
    private Integer booksId;
    private String title;
    private String summary;
    private String isbn;
    private String language;
    private Integer publicationYear;
    private PublisherEntity publisher;
    private List<AuthorEntity> authors;
    private List<BookGenreEntity> genres;
    private List<BookCopyEntity> copies;
    private String imagePath;

    public BookEntity(String title, String summary, String isbn, String language, Integer publicationYear, PublisherEntity publisher) {
        this.title = title;
        this.summary = summary;
        this.isbn = isbn;
        this.language = language;
        this.publicationYear = publicationYear;
        this.publisher = publisher;
    }

    @Override
    public String toString() {
        return title;
    }

    public void setCopies(int copyCount) {
    }
}