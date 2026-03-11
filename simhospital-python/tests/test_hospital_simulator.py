from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import hl7

from simhospital.clock import SyntheticClock
from simhospital.config import SimulatorConfig
from simhospital.simulator import HospitalSimulator

PATHWAYS_DIR = Path(__file__).resolve().parent.parent.parent / "simhospital" / "configs" / "pathways"
START_TIME = datetime(2024, 1, 1, tzinfo=timezone.utc)
SEED = 42


def _config() -> SimulatorConfig:
    return SimulatorConfig(
        pathway_directory=PATHWAYS_DIR,
        concurrent_patients=10,
        clock=SyntheticClock(START_TIME),
        seed=SEED,
        message_rate_per_second=0.0,
    )


def _extract_field(encoded: str, segment_name: str, field_index: int) -> str:
    for seg in encoded.split("\r"):
        if not seg.startswith(segment_name):
            continue
        fields = seg.split("|")
        if len(fields) > field_index:
            return fields[field_index]
    return ""


def test_messages_are_in_non_decreasing_timestamp_order():
    sim = HospitalSimulator(_config())

    previous_ts = ""
    for i in range(1000):
        msg = sim.next_message()
        msh7 = _extract_field(msg, "MSH", 6)

        assert msh7, f"MSH-7 should not be empty at message {i}"
        assert msh7 >= previous_ts, (
            f"Timestamps must be non-decreasing at message {i}: '{previous_ts}' > '{msh7}'"
        )
        previous_ts = msh7


def test_admission_before_discharge_for_same_mrn():
    sim = HospitalSimulator(_config())

    mrn_messages: dict[str, list[str]] = defaultdict(list)
    for _ in range(500):
        msg = sim.next_message()
        mrn = _extract_field(msg, "PID", 3)
        msg_type = _extract_field(msg, "MSH", 8)
        mrn_messages[mrn].append(msg_type)

    for mrn, types in mrn_messages.items():
        a01_indices = [i for i, t in enumerate(types) if "A01" in t]
        a03_indices = [i for i, t in enumerate(types) if "A03" in t]
        if a01_indices and a03_indices:
            assert a01_indices[0] < a03_indices[0], (
                f"ADT_A01 should come before ADT_A03 for MRN {mrn}"
            )


def test_seeded_simulators_produce_same_sequence():
    sim1 = HospitalSimulator(_config())
    sim2 = HospitalSimulator(_config())

    for i in range(100):
        msg1 = sim1.next_message()
        msg2 = sim2.next_message()

        msh7_1 = _extract_field(msg1, "MSH", 6)
        msh7_2 = _extract_field(msg2, "MSH", 6)
        assert msh7_1 == msh7_2, f"MSH-7 mismatch at message {i}"

        msh9_1 = _extract_field(msg1, "MSH", 8)
        msh9_2 = _extract_field(msg2, "MSH", 8)
        assert msh9_1 == msh9_2, f"MSH-9 mismatch at message {i}"


def test_smoke_100_messages_with_mixed_types():
    sim = HospitalSimulator(_config())
    message_names = set()

    for _ in range(100):
        msg = sim.next_message()
        parsed = hl7.parse(msg)
        assert parsed is not None
        msh9 = _extract_field(msg, "MSH", 8)
        message_names.add(msh9)

    assert any("A01" in n for n in message_names), "Should produce ADT^A01 messages"
    assert any("A03" in n for n in message_names), "Should produce ADT^A03 messages"
    assert len(message_names) >= 2, "Should produce at least 2 different message types"
