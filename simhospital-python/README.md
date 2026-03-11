# SimHospital Python

A Python library that simulates a hospital's HL7v2 message stream. Ported from the [Java implementation](../simhospital-java/), originally based on Google's [SimHospital](https://github.com/google/simhospital) Go project.

## Requirements

- Python 3.12+
- [uv](https://docs.astral.sh/uv/) for dependency management

## Setup & Test

```bash
uv sync --all-extras
uv run pytest
```

## Usage

```python
from datetime import datetime, timezone
from pathlib import Path

from simhospital import HospitalSimulator, SimulatorConfig, SyntheticClock

config = SimulatorConfig(
    pathway_directory=Path("path/to/pathways"),  # directory of YAML pathway files
    concurrent_patients=10,
    clock=SyntheticClock(datetime(2024, 1, 1, tzinfo=timezone.utc)),
    seed=42,
    message_rate_per_second=0.0,  # 0 = no throttle
)

sim = HospitalSimulator(config)

for _ in range(100):
    msg = sim.next_message()  # returns pipe-delimited HL7v2 string
    print(msg)
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

`MessageFactory` builds pipe-delimited HL7v2 strings:

| Event Type | Message Type | Trigger |
|------------|-------------|---------|
| ADMISSION | ADT | A01 |
| DISCHARGE | ADT | A03 |
| TRANSFER | ADT | A02 |
| ORDER | ORM | O01 (ORC-1 = NW) |
| CANCEL_ORDER | ORM | O01 (ORC-1 = CA) |
| RESULT | ORU | R01 |
| OBSERVATION | ORU | R01 |
| DELAY | *(none)* | *(skipped)* |

Every message includes MSH (control, datetime, version 2.5), PID (MRN, name, DOB, gender), and PV1 (ward, bed, admission time) segments. Results include OBR/OBX with clinically normal lab values.

### Simulation Loop

`HospitalSimulator.next_message()` runs entirely on the calling thread:

1. Pop the earliest `ScheduledEvent` from the heap
2. Advance the clock to the event's scheduled time
3. Apply side effects (admission sets ward/time, discharge clears it)
4. Build the HL7v2 message via `MessageFactory`
5. Advance the patient to their next pathway step (or replace with a new patient if the pathway is complete)
6. Return the message (or loop if the event was a DELAY)

### Threading Model

`HospitalSimulator` is **single-threaded by design**. To run multiple parallel streams, create one instance per thread with the same config and different seeds.

## Project Structure

```
src/simhospital/
  __init__.py              Public API exports
  clock.py                 SimulationClock ABC, WallClock, SyntheticClock
  config.py                SimulatorConfig (frozen dataclass, all fields required)
  patient.py               PatientState, PatientGenerator, OrderDetails
  pathway.py               PathwayEventType (35 types), PathwayEvent, Pathway, PathwayLoader
  message.py               MessageFactory (pipe-delimited HL7v2 builder)
  simulator.py             HospitalSimulator, ScheduledEvent

tests/
  test_synthetic_clock.py      Advance, no-backward, idempotency
  test_pathway_loader.py       YAML loading, event type recognition
  test_message_factory.py      All event types, hl7 parsing, field population
  test_hospital_simulator.py   Ordering, sequencing, determinism, smoke test
```

## Dependencies

- **python-hl7** 0.4.5 — HL7v2 message parsing and validation
- **PyYAML** 6.0 — YAML pathway file parsing
- **pytest** 8.0 — testing (dev dependency)

No frameworks, no servers, no network transport.
