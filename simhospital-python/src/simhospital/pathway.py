from dataclasses import dataclass
from datetime import timedelta
from enum import Enum, auto
from pathlib import Path
import re
import yaml


class PathwayEventType(Enum):
    ADMISSION = auto()
    DISCHARGE = auto()
    TRANSFER = auto()
    ORDER = auto()
    RESULT = auto()
    OBSERVATION = auto()
    CANCEL_ORDER = auto()
    DELAY = auto()
    REGISTRATION = auto()
    PRE_ADMISSION = auto()
    ADD_PERSON = auto()
    UPDATE_PERSON = auto()
    MERGE = auto()
    BED_SWAP = auto()
    TRANSFER_IN_ERROR = auto()
    DISCHARGE_IN_ERROR = auto()
    CANCEL_VISIT = auto()
    CANCEL_TRANSFER = auto()
    CANCEL_DISCHARGE = auto()
    PENDING_ADMISSION = auto()
    PENDING_DISCHARGE = auto()
    PENDING_TRANSFER = auto()
    CANCEL_PENDING_ADMISSION = auto()
    CANCEL_PENDING_DISCHARGE = auto()
    CANCEL_PENDING_TRANSFER = auto()
    DELETE_VISIT = auto()
    TRACK_DEPARTURE = auto()
    TRACK_ARRIVAL = auto()
    CLINICAL_NOTE = auto()
    DOCUMENT = auto()
    GENERIC = auto()
    HARDCODED_MESSAGE = auto()
    USE_PATIENT = auto()
    AUTO_GENERATE = auto()
    GENERATE_RESOURCES = auto()


@dataclass(frozen=True)
class PathwayEvent:
    type: PathwayEventType
    delay: timedelta | None  # None = fire immediately after prior event
    parameters: dict[str, str]


@dataclass(frozen=True)
class Pathway:
    name: str
    events: list[PathwayEvent]


_STEP_TYPE_MAP: dict[str, PathwayEventType] = {
    "admission": PathwayEventType.ADMISSION,
    "discharge": PathwayEventType.DISCHARGE,
    "transfer": PathwayEventType.TRANSFER,
    "order": PathwayEventType.ORDER,
    "result": PathwayEventType.RESULT,
    "delay": PathwayEventType.DELAY,
    "registration": PathwayEventType.REGISTRATION,
    "pre_admission": PathwayEventType.PRE_ADMISSION,
    "add_person": PathwayEventType.ADD_PERSON,
    "update_person": PathwayEventType.UPDATE_PERSON,
    "merge": PathwayEventType.MERGE,
    "bed_swap": PathwayEventType.BED_SWAP,
    "transfer_in_error": PathwayEventType.TRANSFER_IN_ERROR,
    "discharge_in_error": PathwayEventType.DISCHARGE_IN_ERROR,
    "cancel_visit": PathwayEventType.CANCEL_VISIT,
    "cancel_transfer": PathwayEventType.CANCEL_TRANSFER,
    "cancel_discharge": PathwayEventType.CANCEL_DISCHARGE,
    "pending_admission": PathwayEventType.PENDING_ADMISSION,
    "pending_discharge": PathwayEventType.PENDING_DISCHARGE,
    "pending_transfer": PathwayEventType.PENDING_TRANSFER,
    "cancel_pending_admission": PathwayEventType.CANCEL_PENDING_ADMISSION,
    "cancel_pending_discharge": PathwayEventType.CANCEL_PENDING_DISCHARGE,
    "cancel_pending_transfer": PathwayEventType.CANCEL_PENDING_TRANSFER,
    "delete_visit": PathwayEventType.DELETE_VISIT,
    "track_departure": PathwayEventType.TRACK_DEPARTURE,
    "track_arrival": PathwayEventType.TRACK_ARRIVAL,
    "use_patient": PathwayEventType.USE_PATIENT,
    "autogenerate": PathwayEventType.AUTO_GENERATE,
    "clinical_note": PathwayEventType.CLINICAL_NOTE,
    "hardcoded_message": PathwayEventType.HARDCODED_MESSAGE,
    "document": PathwayEventType.DOCUMENT,
    "generic": PathwayEventType.GENERIC,
    "generate_resources": PathwayEventType.GENERATE_RESOURCES,
}

_DURATION_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(ms|h|m|s)")


def parse_go_duration(s: str) -> timedelta:
    total_ms = 0.0
    for match in _DURATION_RE.finditer(s):
        val = float(match.group(1))
        unit = match.group(2)
        if unit == "h":
            total_ms += val * 3_600_000
        elif unit == "m":
            total_ms += val * 60_000
        elif unit == "s":
            total_ms += val * 1_000
        elif unit == "ms":
            total_ms += val
    return timedelta(milliseconds=max(total_ms, 0))


def _flatten_map(prefix: str, mapping: dict, out: dict[str, str]) -> None:
    for key, value in mapping.items():
        full_key = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            _flatten_map(full_key, value, out)
        else:
            out[full_key] = str(value) if value is not None else ""


def _parse_delay(params: dict[str, str]) -> timedelta:
    from_str = params.get("from")
    if from_str is None:
        raise ValueError("Delay step missing 'from' field")

    to_str = params.get("to")
    if to_str is None:
        return parse_go_duration(from_str)

    from_ms = parse_go_duration(from_str).total_seconds() * 1000
    to_ms = parse_go_duration(to_str).total_seconds() * 1000
    mid_ms = max((from_ms + to_ms) / 2, 1)
    return timedelta(milliseconds=mid_ms)


def _convert_step(step: dict) -> PathwayEvent | None:
    params: dict[str, str] = {}
    raw_params = step.get("parameters")
    if isinstance(raw_params, dict):
        _flatten_map("", raw_params, params)

    for key, value in step.items():
        if key == "parameters":
            continue

        event_type = _STEP_TYPE_MAP.get(key)
        if event_type is None:
            continue

        if isinstance(value, dict):
            for k, v in value.items():
                params[str(k)] = str(v) if v is not None else ""

        delay = _parse_delay(params) if event_type == PathwayEventType.DELAY else None
        return PathwayEvent(type=event_type, delay=delay, parameters=params)

    return None


class PathwayLoader:
    def load(self, directory: Path) -> dict[str, Pathway]:
        if not directory.is_dir():
            raise ValueError(f"Not a directory: {directory}")

        result: dict[str, Pathway] = {}
        for path in sorted(directory.iterdir()):
            if path.suffix not in (".yml", ".yaml", ".json"):
                continue
            self._load_file(path, result)

        if not result:
            raise ValueError(f"No recognisable pathway files found in {directory}")
        return result

    def _load_file(self, path: Path, result: dict[str, Pathway]) -> None:
        with open(path) as f:
            raw = yaml.safe_load(f)
        if not isinstance(raw, dict):
            return
        for name, raw_pathway in raw.items():
            self._load_single(name, raw_pathway, result)

    def _load_single(self, name: str, raw: dict, result: dict[str, Pathway]) -> None:
        steps = raw.get("pathway")
        if not steps:
            return

        events = [e for step in steps if (e := _convert_step(step)) is not None]
        if not events:
            return
        result[name] = Pathway(name=name, events=events)
