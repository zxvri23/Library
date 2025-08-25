package com.tuvarna.bg.library.entity;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PublisherEntity {
    @EqualsAndHashCode.Include
    private Integer publishersId;
    private String pubName;
    private LocalDate establishedOn;
    private List<BookEntity> books;

    public PublisherEntity(String pubName, LocalDate establishedOn) {
        this.pubName = pubName;
        this.establishedOn = establishedOn;
    }

    @Override
    public String toString() {
        return pubName;
    }
}
