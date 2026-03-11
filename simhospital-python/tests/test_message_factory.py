from datetime import date, datetime, timezone
from random import Random

import hl7
import pytest

from simhospital.message import MessageFactory
from simhospital.patient import PatientState
from simhospital.pathway import PathwayEvent, PathwayEventType


def _make_patient() -> PatientState:
    patient = PatientState()
    patient.mrn = "123456"
    patient.family_name = "Doe"
    patient.given_name = "John"
    patient.date_of_birth = date(1980, 5, 15)
    patient.gender = "M"
    patient.pathway_name = "test"
    patient.ward = "Ward-1"
    patient.bed = "Bed-A1"
    patient.admission_time = datetime(2024, 1, 1, 10, 0, 0, tzinfo=timezone.utc)
    return patient


EVENT_TIME = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

_NON_DELAY_TYPES = [t for t in PathwayEventType if t != PathwayEventType.DELAY]


@pytest.mark.parametrize("event_type", _NON_DELAY_TYPES, ids=lambda t: t.name)
def test_build_returns_non_none_for_non_delay_types(event_type):
    factory = MessageFactory(Random(42))
    event = PathwayEvent(type=event_type, delay=None, parameters={})

    result = factory.build(event, _make_patient(), EVENT_TIME)

    assert result is not None


def test_delay_returns_none():
    from datetime import timedelta
    factory = MessageFactory(Random(42))
    event = PathwayEvent(type=PathwayEventType.DELAY, delay=timedelta(minutes=5), parameters={})

    result = factory.build(event, _make_patient(), EVENT_TIME)

    assert result is None


@pytest.mark.parametrize("event_type", _NON_DELAY_TYPES, ids=lambda t: t.name)
def test_message_parses_as_valid_hl7(event_type):
    factory = MessageFactory(Random(42))
    event = PathwayEvent(type=event_type, delay=None, parameters={})

    encoded = factory.build(event, _make_patient(), EVENT_TIME)
    msg = hl7.parse(encoded)

    assert msg is not None
    assert len(list(msg)) >= 2  # at least MSH + PID


@pytest.mark.parametrize("event_type", _NON_DELAY_TYPES, ids=lambda t: t.name)
def test_msh_and_pid_fields_are_populated(event_type):
    factory = MessageFactory(Random(42))
    event = PathwayEvent(type=event_type, delay=None, parameters={})

    encoded = factory.build(event, _make_patient(), EVENT_TIME)

    assert "20240101120000" in encoded, f"MSH-7 should contain event datetime for {event_type}"
    assert "123456" in encoded, f"PID-3 should contain MRN for {event_type}"
    assert "Doe" in encoded, f"PID-5 should contain patient name for {event_type}"
