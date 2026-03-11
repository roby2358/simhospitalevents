from datetime import datetime, timezone

from simhospital.clock import SyntheticClock


def test_advance_to_sets_now():
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    clock = SyntheticClock(start)

    target = datetime(2024, 1, 1, 1, 0, 0, tzinfo=timezone.utc)
    clock.advance_to(target)

    assert clock.now() == target


def test_advance_to_earlier_is_noop():
    start = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
    clock = SyntheticClock(start)

    earlier = datetime(2024, 1, 1, 6, 0, 0, tzinfo=timezone.utc)
    clock.advance_to(earlier)

    assert clock.now() == start


def test_advance_to_same_time_is_noop():
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    clock = SyntheticClock(start)

    clock.advance_to(start)

    assert clock.now() == start


def test_now_returns_start_initially():
    start = datetime(2024, 6, 15, 10, 30, 0, tzinfo=timezone.utc)
    clock = SyntheticClock(start)

    assert clock.now() == start
