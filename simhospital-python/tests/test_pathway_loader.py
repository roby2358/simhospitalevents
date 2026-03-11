from pathlib import Path

import pytest

from simhospital.pathway import PathwayLoader

PATHWAYS_DIR = Path(__file__).resolve().parent.parent.parent / "simhospital" / "configs" / "pathways"


def test_loads_all_yaml_files():
    loader = PathwayLoader()
    pathways = loader.load(PATHWAYS_DIR)

    assert len(pathways) > 0


def test_each_pathway_has_at_least_one_event():
    loader = PathwayLoader()
    pathways = loader.load(PATHWAYS_DIR)

    for name, pathway in pathways.items():
        assert len(pathway.events) > 0, f"Pathway '{name}' should have at least one event"


def test_all_event_types_are_recognised():
    loader = PathwayLoader()
    pathways = loader.load(PATHWAYS_DIR)

    for name, pathway in pathways.items():
        for event in pathway.events:
            assert event.type is not None, f"Event type should not be None in pathway '{name}'"


def test_raises_on_nonexistent_directory():
    loader = PathwayLoader()
    with pytest.raises(ValueError):
        loader.load(Path("/nonexistent/path"))
