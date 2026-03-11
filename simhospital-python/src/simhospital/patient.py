from dataclasses import dataclass
from datetime import date, datetime, timedelta
from random import Random


@dataclass(frozen=True)
class OrderDetails:
    order_code: str
    description: str


class PatientState:
    __slots__ = (
        "mrn", "family_name", "given_name", "date_of_birth", "gender",
        "pathway_name", "next_event_index", "admission_time", "ward", "bed",
        "open_orders",
    )

    def __init__(self):
        self.mrn: str = ""
        self.family_name: str = ""
        self.given_name: str = ""
        self.date_of_birth: date | None = None
        self.gender: str = "U"
        self.pathway_name: str = ""
        self.next_event_index: int = 0
        self.admission_time: datetime | None = None
        self.ward: str = ""
        self.bed: str = ""
        self.open_orders: dict[str, OrderDetails] = {}


_FAMILY_NAMES = [
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
    "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez",
    "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
    "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark",
    "Ramirez", "Lewis", "Robinson", "Walker", "Young", "Allen", "King",
]

_GIVEN_NAMES = [
    "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael",
    "Linda", "David", "Elizabeth", "William", "Barbara", "Richard", "Susan",
    "Joseph", "Jessica", "Thomas", "Sarah", "Charles", "Karen", "Emma",
    "Oliver", "Charlotte", "Amelia", "Liam", "Noah", "Sophia", "Isabella",
    "Mason", "Lucas", "Ethan", "Alexander", "Ava", "Mia", "Harper",
]

_GENDER_THRESHOLDS = (48, 96)  # M < 48, F < 96, U >= 96


class PatientGenerator:
    def __init__(self, rng: Random):
        self._rng = rng
        self._mrn_counter = 1_000_000

    def generate(self, pathway_name: str) -> PatientState:
        patient = PatientState()
        patient.mrn = str(self._mrn_counter)
        self._mrn_counter += 1
        patient.family_name = self._rng.choice(_FAMILY_NAMES)
        patient.given_name = self._rng.choice(_GIVEN_NAMES)
        patient.date_of_birth = self._random_dob()
        patient.gender = self._random_gender()
        patient.pathway_name = pathway_name
        patient.next_event_index = 0
        patient.ward = f"Ward-{self._rng.randint(1, 20)}"
        patient.bed = f"Bed-{chr(ord('A') + self._rng.randint(0, 5))}{self._rng.randint(1, 10)}"
        return patient

    def _random_dob(self) -> date:
        age_in_days = 365 + self._rng.randint(0, 95 * 365 - 1)
        return date.today() - timedelta(days=age_in_days)

    def _random_gender(self) -> str:
        roll = self._rng.randint(0, 99)
        if roll < _GENDER_THRESHOLDS[0]:
            return "M"
        if roll < _GENDER_THRESHOLDS[1]:
            return "F"
        return "U"
