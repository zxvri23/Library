package com.tuvarna.bg.library.entity;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoleEntity {
    @EqualsAndHashCode.Include
    private Integer rolesId;
    private String name;
    private List<UserEntity> users;

    public RoleEntity(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
