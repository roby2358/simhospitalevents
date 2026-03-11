# SimHospital Java

A Java 21 library that simulates a hospital's HL7v2 message stream. Ported from Google's [SimHospital](https://github.com/google/simhospital) Go project.

## Requirements

- Java 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)

## Build & Test

```bash
./mvnw test
```

## Usage

```java
import com.simhospital.HospitalSimulator;
import com.simhospital.SimulatorConfig;
import com.simhospital.clock.SyntheticClock;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;

var config = new SimulatorConfig(
    Path.of("path/to/pathways"),   // directory of YAML pathway files
    10,                             // concurrent patients
    new SyntheticClock(Instant.parse("2024-01-01T00:00:00Z")),
    42L,                            // random seed for reproducibility
    0.0                             // message rate limit (0 = no throttle)
);

var sim = new HospitalSimulator(config);
var parser = new PipeParser();

for (int i = 0; i < 100; i++) {
    Message msg = sim.nextMessage();
    System.out.println(parser.encode(msg));
}
```

## Architecture

### Clock

| Class | Behaviour |
|-------|-----------|
| `SyntheticClock` | Jumps to event time instantly. Use for fast-forward simulation and testing. |
| `WallClock` | Blocks until real time reaches event time. Use for real-time pacing. |

### Pathway Loading

`PathwayLoader` reads `.yml`, `.yaml`, and `.json` files from a directory. Each file contains named pathways with a sequence of steps:

```yaml
admit_and_discharge:
  percentage_of_patients: 0.5
  pathway:
    - admission:
        loc: Renal
    - delay:
        from: 1h
        to: 4h
    - order:
        order_profile: "CBC"
        order_id: cbc1
    - result:
        order_profile: "CBC"
    - discharge: {}
```

All 35 step types from the Go source are recognised (admission, discharge, transfer, order, result, delay, registration, merge, clinical_note, document, etc.).

### Message Generation

`MessageFactory` builds typed HAPI HL7v2 messages with populated segments:

| Event Type | HAPI Class | Trigger |
|------------|------------|---------|
| ADMISSION | `ADT_A01` | A01 |
| DISCHARGE | `ADT_A03` | A03 |
| TRANSFER | `ADT_A02` | A02 |
| ORDER | `ORM_O01` | O01 (ORC-1 = NW) |
| CANCEL_ORDER | `ORM_O01` | O01 (ORC-1 = CA) |
| RESULT | `ORU_R01` | R01 |
| OBSERVATION | `ORU_R01` | R01 |
| DELAY | *(none)* | *(skipped)* |

Every message includes MSH (control, datetime, version 2.5), PID (MRN, name, DOB, gender), and PV1 (ward, bed, admission time) segments. Results include OBR/OBX with clinically normal lab values.

### Simulation Loop

`HospitalSimulator.nextMessage()` runs entirely on the calling thread:

1. Pop the earliest `ScheduledEvent` from the priority queue
2. Advance the clock to the event's scheduled time
3. Apply side effects (admission sets ward/time, discharge clears it)
4. Build the HL7v2 message via `MessageFactory`
5. Advance the patient to their next pathway step (or replace with a new patient if the pathway is complete)
6. Return the message (or loop if the event was a DELAY)

### Threading Model

`HospitalSimulator` is **single-threaded by design**. To run multiple parallel streams, create one instance per thread with the same config and different seeds.

## Project Structure

```
src/main/java/com/simhospital/
  HospitalSimulator.java          Main entry point
  SimulatorConfig.java             All config, no defaults
  clock/
    SimulationClock.java           Interface
    WallClock.java                 Real-time
    SyntheticClock.java            Fast-forward
  patient/
    PatientState.java              Mutable patient state
    PatientGenerator.java          Seeded random demographics
    OrderDetails.java              Immutable order record
  pathway/
    Pathway.java                   Name + event list
    PathwayEvent.java              Type + delay + parameters
    PathwayEventType.java          35 event types
    PathwayLoader.java             YAML parser
  message/
    MessageFactory.java            HAPI message builder
  simulator/
    ScheduledEvent.java            Priority queue entry

src/test/java/com/simhospital/
  HospitalSimulatorTest.java       Ordering, sequencing, determinism, smoke test
  MessageFactoryTest.java          All event types, round-trips, field population
  PathwayLoaderTest.java           YAML loading, event type recognition
  SyntheticClockTest.java          Advance, no-backward, idempotency
```

## Dependencies

- **HAPI HL7v2** 2.5.1 — message parsing and structure definitions
- **Jackson** 2.17.2 — YAML/JSON pathway file parsing
- **JUnit 5** 5.11.3 — testing

No Spring, no application server, no MLLP/HTTP.
