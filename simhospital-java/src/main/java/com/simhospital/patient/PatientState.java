package com.simhospital.patient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class PatientState {
    private String mrn;
    private String familyName;
    private String givenName;
    private LocalDate dateOfBirth;
    private String gender; // "M", "F", or "U"
    private String pathwayName;
    private int nextEventIndex;
    private Instant admissionTime;
    private String ward;
    private String bed;
    private Map<String, OrderDetails> openOrders;

    public PatientState() {
        this.openOrders = new HashMap<>();
    }

    public String getMrn() { return mrn; }
    public void setMrn(String mrn) { this.mrn = mrn; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPathwayName() { return pathwayName; }
    public void setPathwayName(String pathwayName) { this.pathwayName = pathwayName; }

    public int getNextEventIndex() { return nextEventIndex; }
    public void setNextEventIndex(int nextEventIndex) { this.nextEventIndex = nextEventIndex; }

    public Instant getAdmissionTime() { return admissionTime; }
    public void setAdmissionTime(Instant admissionTime) { this.admissionTime = admissionTime; }

    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }

    public String getBed() { return bed; }
    public void setBed(String bed) { this.bed = bed; }

    public Map<String, OrderDetails> getOpenOrders() { return openOrders; }
    public void setOpenOrders(Map<String, OrderDetails> openOrders) { this.openOrders = openOrders; }
}
