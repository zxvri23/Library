package com.tuvarna.bg.library.entity;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserEntity {

    @EqualsAndHashCode.Include
    private Integer usersId;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String email;
    private RoleEntity role;
    private List<LoanEntity> loans;
    private List<LoanEntity> staffLoans;
    private List<ReservationEntity> reservations;

    public UserEntity(String username, String password, String firstName, String lastName, String email, RoleEntity role) {
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return getFullName() + " (" + username + ")";
    }
}
