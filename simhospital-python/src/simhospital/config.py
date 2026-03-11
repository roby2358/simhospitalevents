from dataclasses import dataclass
from pathlib import Path

from simhospital.clock import SimulationClock


@dataclass(frozen=True)
class SimulatorConfig:
    pathway_directory: Path
    concurrent_patients: int
    clock: SimulationClock
    seed: int
    message_rate_per_second: float
