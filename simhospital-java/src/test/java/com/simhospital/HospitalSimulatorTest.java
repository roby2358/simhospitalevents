package com.simhospital;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import com.simhospital.clock.SyntheticClock;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HospitalSimulatorTest {

    private static final Path PATHWAYS_DIR = Path.of("../simhospital/configs/pathways");
    private static final Instant START_TIME = Instant.parse("2024-01-01T00:00:00Z");
    private static final long SEED = 42L;
    private static final PipeParser PARSER = new PipeParser();

    private SimulatorConfig config() {
        return new SimulatorConfig(PATHWAYS_DIR, 10, new SyntheticClock(START_TIME), SEED, 0.0);
    }

    @Test
    void messagesAreInNonDecreasingTimestampOrder() throws Exception {
        HospitalSimulator sim = new HospitalSimulator(config());

        String previousTimestamp = "";
        for (int i = 0; i < 1000; i++) {
            Message msg = sim.nextMessage();
            String msh7 = extractField(msg, "MSH", 6);

            assertNotNull(msh7, "MSH-7 should not be null at message " + i);
            assertFalse(msh7.isEmpty(), "MSH-7 should not be empty at message " + i);
            assertTrue(msh7.compareTo(previousTimestamp) >= 0,
                    "Timestamps must be non-decreasing at message " + i +
                    ": '" + previousTimestamp + "' > '" + msh7 + "'");
            previousTimestamp = msh7;
        }
    }

    @Test
    void admissionBeforeDischargeForSameMrn() throws Exception {
        HospitalSimulator sim = new HospitalSimulator(config());

        Map<String, List<String>> mrnMessages = new LinkedHashMap<>();
        for (int i = 0; i < 500; i++) {
            Message msg = sim.nextMessage();
            String mrn = extractField(msg, "PID", 3);
            String msgType = extractField(msg, "MSH", 8);
            mrnMessages.computeIfAbsent(mrn, k -> new ArrayList<>()).add(msgType);
        }

        for (Map.Entry<String, List<String>> entry : mrnMessages.entrySet()) {
            List<String> types = entry.getValue();
            int a01 = findFirst(types, "A01");
            int a03 = findFirst(types, "A03");
            if (a01 >= 0 && a03 >= 0) {
                assertTrue(a01 < a03,
                        "ADT_A01 should come before ADT_A03 for MRN " + entry.getKey());
            }
        }
    }

    @Test
    void seededSimulatorsProduceSameSequence() throws Exception {
        HospitalSimulator sim1 = new HospitalSimulator(config());
        HospitalSimulator sim2 = new HospitalSimulator(config());

        for (int i = 0; i < 100; i++) {
            Message msg1 = sim1.nextMessage();
            Message msg2 = sim2.nextMessage();

            assertEquals(extractField(msg1, "MSH", 6), extractField(msg2, "MSH", 6),
                    "MSH-7 mismatch at message " + i);
            assertEquals(extractField(msg1, "MSH", 8), extractField(msg2, "MSH", 8),
                    "MSH-9 mismatch at message " + i);
        }
    }

    @Test
    void smokeTest_100MessagesWithMixedTypes() throws Exception {
        var sim = new HospitalSimulator(config());
        Set<String> messageNames = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            Message msg = sim.nextMessage();
            String encoded = PARSER.encode(msg);
            assertNotNull(encoded);
            assertFalse(encoded.isEmpty());
            messageNames.add(msg.getName());
        }

        assertTrue(messageNames.contains("ADT_A01"), "Should produce ADT_A01 messages");
        assertTrue(messageNames.contains("ADT_A03"), "Should produce ADT_A03 messages");
        assertTrue(messageNames.size() >= 2, "Should produce at least 2 different message types");
    }

    private static String extractField(Message msg, String segmentName, int fieldIndex) throws HL7Exception {
        String encoded = PARSER.encode(msg);
        for (String seg : encoded.split("\r")) {
            if (!seg.startsWith(segmentName)) continue;
            String[] fields = seg.split("\\|", -1);
            if (fields.length > fieldIndex) return fields[fieldIndex];
        }
        return "";
    }

    private static int findFirst(List<String> types, String needle) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).contains(needle)) return i;
        }
        return -1;
    }
}
