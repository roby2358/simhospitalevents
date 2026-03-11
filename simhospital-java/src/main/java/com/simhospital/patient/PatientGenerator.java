package com.simhospital.patient;

import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class PatientGenerator {

    private static final String[] FAMILY_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
        "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez",
        "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
        "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark",
        "Ramirez", "Lewis", "Robinson", "Walker", "Young", "Allen", "King"
    };

    private static final String[] GIVEN_NAMES = {
        "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael",
        "Linda", "David", "Elizabeth", "William", "Barbara", "Richard", "Susan",
        "Joseph", "Jessica", "Thomas", "Sarah", "Charles", "Karen", "Emma",
        "Oliver", "Charlotte", "Amelia", "Liam", "Noah", "Sophia", "Isabella",
        "Mason", "Lucas", "Ethan", "Alexander", "Ava", "Mia", "Harper"
    };

    private static final String[] GENDERS = {"M", "F", "U"};
    private static final int[] GENDER_WEIGHTS = {48, 48, 4}; // approximate distribution

    private final Random random;
    private final AtomicLong mrnCounter;

    public PatientGenerator(Random random) {
        this.random = random;
        this.mrnCounter = new AtomicLong(1_000_000);
    }

    public PatientState generate(String pathwayName) {
        PatientState patient = new PatientState();
        patient.setMrn(String.valueOf(mrnCounter.getAndIncrement()));
        patient.setFamilyName(FAMILY_NAMES[random.nextInt(FAMILY_NAMES.length)]);
        patient.setGivenName(GIVEN_NAMES[random.nextInt(GIVEN_NAMES.length)]);
        patient.setDateOfBirth(randomDateOfBirth());
        patient.setGender(randomGender());
        patient.setPathwayName(pathwayName);
        patient.setNextEventIndex(0);
        patient.setWard("Ward-" + (random.nextInt(20) + 1));
        patient.setBed("Bed-" + (char) ('A' + random.nextInt(6)) + (random.nextInt(10) + 1));
        return patient;
    }

    private LocalDate randomDateOfBirth() {
        // Ages between 1 and 95 years
        int ageInDays = 365 + random.nextInt(95 * 365);
        return LocalDate.now().minusDays(ageInDays);
    }

    private String randomGender() {
        int roll = random.nextInt(100);
        if (roll < GENDER_WEIGHTS[0]) return GENDERS[0];
        if (roll < GENDER_WEIGHTS[0] + GENDER_WEIGHTS[1]) return GENDERS[1];
        return GENDERS[2];
    }
}
