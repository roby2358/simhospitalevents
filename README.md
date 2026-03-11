# SimHospital Events

A Java port of Google's [SimHospital](https://github.com/google/simhospital) simulation engine, producing realistic HL7v2 message streams for performance testing healthcare integration systems.

## Repository Structure

```
simhospital/          # Original Go source (reference only, gitignored)
simhospital-java/     # Java 21 port — the deliverable
```

## Quick Start

```bash
cd simhospital-java
./mvnw test
```

See [simhospital-java/README.md](simhospital-java/README.md) for full usage, API reference, and architecture details.

## What This Produces

The simulator reads YAML pathway definitions (admission, discharge, transfer, order, result, etc.) and generates a time-ordered stream of well-formed HAPI HL7v2 `Message` objects:

- `ADT_A01` (Admission), `ADT_A02` (Transfer), `ADT_A03` (Discharge)
- `ORM_O01` (Orders — new and cancel)
- `ORU_R01` (Results and observations)
- 30+ additional ADT event types

This is a **library JAR** — no server, no HTTP, no MLLP. The caller controls transport and threading.

## License

The original SimHospital source is licensed under the Apache License 2.0 by Google LLC.
