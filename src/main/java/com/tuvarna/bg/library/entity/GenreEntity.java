package com.tuvarna.bg.library.entity;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GenreEntity {

    @EqualsAndHashCode.Include
    private Integer genresId;
    private String genreName;
    private String genreDescription;
    private List<BookEntity> books;


    public GenreEntity(Integer genresId, String genreName, String genreDescription) {
        this.genresId = genresId;
        this.genreName = genreName;
        this.genreDescription = genreDescription;
    }

    public String toString(){
        return genreName;
    }
}
