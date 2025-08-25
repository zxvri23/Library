package com.tuvarna.bg.library.entity;

import jdk.jfr.DataAmount;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@DataAmount
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuthorEntity {
    @EqualsAndHashCode.Include
    private Integer authorsId;
    private String fullName;
    private LocalDate birthDate;
    private List<BookEntity> books;

    public AuthorEntity(String fullName, LocalDate birthDate) {
        this.fullName = fullName;
        this.birthDate = birthDate;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
