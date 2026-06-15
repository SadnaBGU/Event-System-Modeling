package com.eventsystem.infrastructure.persistence.entities;




import java.time.LocalDate;

import jakarta.persistence.Embeddable;

@Embeddable
public class PersonalDetailsEmbeddable {

    private String firstName;
    private String lastName;
    private String email;
    private LocalDate dateOfBirth;

    protected PersonalDetailsEmbeddable() {}

    public PersonalDetailsEmbeddable(String firstName, String lastName, String email, LocalDate dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
    }

    // getters


    // ---------------- GETTERS ----------------

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }


    
}