"""J-002 actual Python/Java quaternion karşılaştırması; test-only developer aracı."""

import argparse
import math
import os
import platform
import subprocess
import sys
from pathlib import Path

import numpy as np
import pytest

CORE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CORE.parent / "roketsim-native-engine/src"))
from roketsim_native.math import quaternion as reference

# Mevcut test_quaternion.py TEST_ATOL / test_math_foundation.py VV_ATOL.
TEST_ATOL = 1e-12
parser = argparse.ArgumentParser()
parser.add_argument("--java", required=True)
args = parser.parse_args()
classpath = os.pathsep.join(str(CORE / directory) for directory in
    ("build/classes/java/main", "build/classes/java/test"))


def encoded(value):
    if isinstance(value, (list, tuple, np.ndarray)):
        return ",".join(encoded(item) for item in value)
    if math.isnan(value):
        return "NaN"
    if math.isinf(value):
        return "Infinity" if value > 0 else "-Infinity"
    return repr(float(value))


def java_call(operation, *values):
    output = subprocess.check_output([args.java, "-cp", classpath,
        "org.roketsim.core.QuaternionParityProbe", operation, *map(encoded, values)], text=True)
    status, *parts = output.strip().split("\t")
    return status, parts


def python_call(operation, *values):
    match operation:
        case "as": return reference.as_quaternion(values[0])
        case "norm": return reference.quaternion_norm(values[0])
        case "normalize": return reference.normalize_quaternion(values[0], atol=values[1])
        case "conjugate": return reference.conjugate(values[0])
        case "multiply": return reference.multiply(*values)
        case "inverse": return reference.inverse(values[0], atol=values[1])
        case "axis": return reference.from_axis_angle(values[0], values[1], atol=values[2])
        case "body": return reference.rotate_body_to_world(values[0], values[1], atol=values[2])
        case "world": return reference.rotate_world_to_body(values[0], values[1], atol=values[2])
        case "matrix": return reference.to_rotation_matrix(values[0], atol=values[1])
        case "increment": return reference.apply_increment_left(values[0], values[1], atol=values[2])
        case _: raise AssertionError(operation)


successes = failures = 0


def compare(operation, *values, error_token=None):
    global successes, failures
    try:
        expected = python_call(operation, *values)
    except ValueError:
        status, parts = java_call(operation, *values)
        assert status == "ERROR", (operation, values, status, parts)
        if error_token:
            assert error_token in parts[0], (operation, values, parts)
        failures += 1
        return
    status, parts = java_call(operation, *values)
    assert status == "OK", (operation, values, parts)
    actual = np.array([float(value) for value in parts])
    expected = np.asarray(expected).reshape(-1)
    if operation == "norm":
        assert actual[0] == pytest.approx(expected[0])
    elif operation in {"as", "conjugate", "multiply"}:
        np.testing.assert_array_equal(actual, expected)
    else:
        np.testing.assert_allclose(actual, expected, atol=TEST_ATOL, rtol=0.)
    successes += 1


probe = [3.5, -1.25, 8.]
delta = reference.from_axis_angle([0., 0., 1.], -.2, atol=0.)
for q in ([1.,0.,0.,0.], [1.,2.,3.,4.], [2.,-1.,.5,3.],
          reference.from_axis_angle([1.,-2.,.5],1.234,atol=0.)):
    for op in ("as", "norm", "conjugate"):
        compare(op, q)
    for op in ("normalize", "inverse", "matrix"):
        compare(op, q, 0.)
    compare("multiply", q, delta)
    compare("multiply", delta, q)
    compare("body", q, probe, 0.)
    compare("world", q, probe, 0.)
    compare("increment", q, delta, 0.)

for axis, angle in (([1.,0.,0.],0.), ([2.,0.,0.],math.pi/2),
                    ([0.,1.,0.],math.pi/2), ([0.,0.,1.],-math.pi/2),
                    ([1.,2.,3.],.845), ([-2.,5.,1.],.93),
                    ([1.,4.,-2.],1.1), ([0.,0.,5.],math.pi)):
    compare("axis", axis, angle, 0.)
    q = reference.from_axis_angle(axis,angle,atol=0.)
    compare("body", q, probe, TEST_ATOL)
    compare("body", -q, probe, TEST_ATOL)
    compare("body", 7.5*q, probe, TEST_ATOL)
    compare("world", q, reference.rotate_body_to_world(q,probe,atol=TEST_ATOL),TEST_ATOL)
    compare("matrix", q, TEST_ATOL)

compare("as", [0.,0.,0.,0.])
compare("norm", [0.,0.,0.,0.])
compare("conjugate", [0.,0.,0.,0.])
for q in ([0.,0.,0.,0.], [1e-9,0.,0.,0.], [1e-8,0.,0.,0.]):
    for op in ("normalize", "inverse", "matrix"):
        compare(op, q, 1e-8, error_token="quaternion norm")
for tolerance in (-1.,math.nan,math.inf,-math.inf):
    for op in ("normalize", "inverse", "matrix"):
        compare(op,[1.,0.,0.,0.],tolerance,error_token="atol")
    compare("axis",[1.,0.,0.],0.,tolerance,error_token="atol")
    compare("body",[1.,0.,0.,0.],probe,tolerance,error_token="atol")
    compare("world",[1.,0.,0.,0.],probe,tolerance,error_token="atol")
    compare("increment",[1.,0.,0.,0.],delta,tolerance,error_token="atol")
for value in (math.nan, math.inf, -math.inf):
    compare("as",[1.,0.,value,0.],error_token="finite")
    compare("axis",[1.,0.,0.],value,0.,error_token="angle_rad")
for wrong in ([],[1.,2.,3.],[1.,2.,3.,4.,5.]):
    compare("as",wrong,error_token="shape")
compare("axis",[1.,0.],0.,0.,error_token="shape")
compare("axis",[0.,0.],0.,0.,error_token="magnitude")
compare("axis",[1e-9,0.,0.],0.,1e-8,error_token="magnitude")
compare("body",[0.,0.,0.,0.],[1.,2.],0.,error_token="quaternion norm")
compare("body",[1.,0.,0.,0.],[1.,2.],0.,error_token="shape")
compare("norm",[sys.float_info.max,0.,0.,0.],error_token="finite")
compare("inverse",[float.fromhex('0x0.0000000000001p-1022'),0.,0.,0.],0.,error_token="quaternion norm")

trig_max_error = 0.
for angle in (0.,math.pi/4,-math.pi/4,math.pi/2,.63/2,1.234/2,1.1/2):
    status, parts = java_call("trig",angle)
    assert status == "OK"
    result = np.array([float(value) for value in parts])
    expected = np.array([math.sin(angle),math.cos(angle)]*2)
    np.testing.assert_allclose(result,expected,atol=TEST_ATOL,rtol=0.)
    trig_max_error = max(trig_max_error,float(np.max(np.abs(result-expected))))

print(f"QUATERNION_PARITY: {successes} success cases, {failures} error cases passed")
print(f"MATH_STRICTMATH: 7 cases passed; max Python difference={trig_max_error}")
print(sys.version)
print("NumPy",np.__version__,platform.platform())
