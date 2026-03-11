from abc import ABC, abstractmethod
from datetime import datetime, timezone
import time


class SimulationClock(ABC):
    @abstractmethod
    def now(self) -> datetime:
        ...

    @abstractmethod
    def advance_to(self, target: datetime) -> None:
        ...


class WallClock(SimulationClock):
    def now(self) -> datetime:
        return datetime.now(timezone.utc)

    def advance_to(self, target: datetime) -> None:
        while True:
            current = datetime.now(timezone.utc)
            if current >= target:
                return
            remaining = (target - current).total_seconds()
            time.sleep(remaining)


class SyntheticClock(SimulationClock):
    def __init__(self, start_time: datetime):
        self._current_time = start_time

    def now(self) -> datetime:
        return self._current_time

    def advance_to(self, target: datetime) -> None:
        if target > self._current_time:
            self._current_time = target
