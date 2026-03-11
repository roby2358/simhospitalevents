package com.simhospital;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import com.simhospital.message.MessageFactory;
import com.simhospital.patient.PatientState;
import com.simhospital.pathway.PathwayEvent;
import com.simhospital.pathway.PathwayEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MessageFactoryTest {

    private MessageFactory factory;
    private PatientState patient;
    private Instant eventTime;

    @BeforeEach
    void setUp() {
        factory = new MessageFactory(new Random(42));
        patient = new PatientState();
        patient.setMrn("123456");
        patient.setFamilyName("Doe");
        patient.setGivenName("John");
        patient.setDateOfBirth(LocalDate.of(1980, 5, 15));
        patient.setGender("M");
        patient.setPathwayName("test");
        patient.setWard("Ward-1");
        patient.setBed("Bed-A1");
        patient.setAdmissionTime(Instant.parse("2024-01-01T10:00:00Z"));
        eventTime = Instant.parse("2024-01-01T12:00:00Z");
    }

    @ParameterizedTest
    @EnumSource(value = PathwayEventType.class, names = "DELAY", mode = EnumSource.Mode.EXCLUDE)
    void buildReturnsNonNullForNonDelayTypes(PathwayEventType type) {
        PathwayEvent event = new PathwayEvent(type, null, Map.of());
        Message result = factory.build(event, patient, eventTime);
        assertNotNull(result, "build() should return non-null for " + type);
    }

    @Test
    void delayReturnsNull() {
        PathwayEvent event = new PathwayEvent(PathwayEventType.DELAY, Duration.ofMinutes(5), Map.of());
        Message result = factory.build(event, patient, eventTime);
        assertNull(result, "DELAY should return null");
    }

    @ParameterizedTest
    @EnumSource(value = PathwayEventType.class, names = "DELAY", mode = EnumSource.Mode.EXCLUDE)
    void messageRoundTripsThroughPipeParser(PathwayEventType type) throws Exception {
        PathwayEvent event = new PathwayEvent(type, null, Map.of());
        Message msg = factory.build(event, patient, eventTime);

        PipeParser parser = new PipeParser();
        String encoded = parser.encode(msg);

        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());

        Message reparsed = parser.parse(encoded);
        assertNotNull(reparsed);
    }

    @ParameterizedTest
    @EnumSource(value = PathwayEventType.class, names = "DELAY", mode = EnumSource.Mode.EXCLUDE)
    void mshAndPidFieldsArePopulated(PathwayEventType type) throws Exception {
        PathwayEvent event = new PathwayEvent(type, null, Map.of());
        Message msg = factory.build(event, patient, eventTime);

        PipeParser parser = new PipeParser();
        String encoded = parser.encode(msg);

        assertTrue(encoded.contains("20240101120000"),
                "MSH-7 should contain event datetime for " + type);
        assertTrue(encoded.contains("123456"),
                "PID-3 should contain MRN for " + type);
        assertTrue(encoded.contains("Doe"),
                "PID-5 should contain patient name for " + type);
    }
}
