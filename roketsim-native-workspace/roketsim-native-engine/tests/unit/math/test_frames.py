"""NAT-006 dondurulmuş frame convention unit testleri."""

import pytest

import roketsim_native.math.frames as frames
from roketsim_native.math.frames import (
    FRAME_AXES,
    GEOMETRY_AXIAL_CONVENTION,
    ReferenceFrame,
)


def test_reference_frame_contains_only_world_enu_and_body() -> None:
    assert list(ReferenceFrame) == [
        ReferenceFrame.WORLD_ENU,
        ReferenceFrame.BODY,
    ]


@pytest.mark.parametrize(
    ("index", "identifier", "meaning"),
    [
        (0, "x_W", "East"),
        (1, "y_W", "North"),
        (2, "z_W", "Up"),
    ],
)
def test_world_enu_axis_convention(
    index: int,
    identifier: str,
    meaning: str,
) -> None:
    axis = FRAME_AXES[ReferenceFrame.WORLD_ENU][index]

    assert axis.identifier == identifier
    assert axis.meaning == meaning


@pytest.mark.parametrize(
    ("index", "identifier"),
    [(0, "x_B"), (1, "y_B"), (2, "z_B")],
)
def test_body_axis_identifiers(index: int, identifier: str) -> None:
    assert FRAME_AXES[ReferenceFrame.BODY][index].identifier == identifier


def test_body_z_axis_semantics() -> None:
    z_body = FRAME_AXES[ReferenceFrame.BODY][2]

    assert z_body.meaning == "longitudinal / thrust / roll axis"


def test_geometry_axial_convention() -> None:
    assert dict(GEOMETRY_AXIAL_CONVENTION) == {
        "origin": "nose tip",
        "coordinate": "x_geo",
        "positive_direction": "nose -> tail",
    }


def test_geometry_coordinate_is_not_body_longitudinal_axis() -> None:
    geometry_coordinate = GEOMETRY_AXIAL_CONVENTION["coordinate"]
    body_longitudinal_axis = FRAME_AXES[ReferenceFrame.BODY][2].identifier

    assert geometry_coordinate != body_longitudinal_axis
    assert geometry_coordinate not in {frame.value for frame in ReferenceFrame}


def test_convention_metadata_is_read_only() -> None:
    with pytest.raises(TypeError):
        FRAME_AXES[ReferenceFrame.BODY] = ()  # type: ignore[index]

    with pytest.raises(TypeError):
        GEOMETRY_AXIAL_CONVENTION["coordinate"] = "z_B"  # type: ignore[index]


@pytest.mark.parametrize(
    "forbidden_name",
    [
        "FramedVector",
        "rotation_matrix",
        "transform_vector",
        "body_to_world",
        "world_to_body",
    ],
)
def test_frames_module_has_no_rotation_or_transform_api(
    forbidden_name: str,
) -> None:
    assert not hasattr(frames, forbidden_name)
