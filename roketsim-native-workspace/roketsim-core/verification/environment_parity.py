"""J-003 actual Python/Java parity; production runtime bağımlılığı değildir."""

import argparse
import math
import os
import platform
import random
import subprocess
import sys
from pathlib import Path
from parity_contract import count_cases

import numpy as np

CORE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CORE.parent / "roketsim-native-engine/src"))
from roketsim_native.environment import altitude, atmosphere, air_properties, gravity, wind
from roketsim_native.flight_conditions import basic, relative_flow

parser = argparse.ArgumentParser()
parser.add_argument("--java", required=True)
parser.add_argument("--build-dir", type=Path)
args = parser.parse_args()
cases = []


def add(operation, *values):
    cases.append((operation, values))


def python_call(operation, values):
    match operation:
        case "orthometric": return altitude.orthometric_from_ellipsoidal(ellipsoidal_height_m=values[0], geoid_undulation_m=values[1])
        case "ellipsoidal": return altitude.ellipsoidal_from_orthometric(orthometric_height_m=values[0], geoid_undulation_m=values[1])
        case "agl": return altitude.agl_from_orthometric(orthometric_height_m=values[0], ground_elevation_m=values[1])
        case "from_agl": return altitude.orthometric_from_agl(agl_altitude_m=values[0], ground_elevation_m=values[1])
        case "bridge": return altitude.atmospheric_geometric_from_orthometric(orthometric_height_m=values[0])
        case "geopotential": return altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=values[0])
        case "geometric": return altitude.geometric_from_geopotential(geopotential_height_m=values[0])
        case "atmosphere":
            state = atmosphere.USStandardAtmosphere1976Lower().evaluate(geopotential_height_m=values[0])
            return state.temperature_K, state.pressure_Pa, state.density_kg_m3
        case "air":
            air = air_properties.DryAirPropertiesCalculator().evaluate(atmosphere_state=atmosphere.DryAirAtmosphereState(*values))
            return air.speed_of_sound_m_s, air.dynamic_viscosity_Pa_s, air.kinematic_viscosity_m2_s, air.specific_heat_ratio
        case "gravity": return gravity.ConstantGravityModel().evaluate()
        case "no_wind": return wind.NoWindModel().evaluate()
        case "wind": return wind.ConstantWindModel(airmass_velocity_world_m_s=values[0]).evaluate()
        case "flow": return relative_flow.RelativeFlowCalculator().evaluate(rocket_velocity_world_m_s=values[0], airmass_velocity_world_m_s=values[1])
        case "conditions":
            v, rho, sound, nu, length = values
            result = basic.BasicFlightConditionsCalculator().evaluate(relative_velocity_world_m_s=v,
                atmosphere_state=atmosphere.DryAirAtmosphereState(math.nan, math.nan, rho),
                air_properties=air_properties.DryAirProperties(sound, math.nan, nu, math.nan), reference_length_m=length)
            return result.airspeed_m_s, result.mach, result.reynolds, result.dynamic_pressure_Pa
        case "chain":
            state = python_call("atmosphere", (values[0],))
            air = python_call("air", state)
            result = python_call("conditions", (values[1], state[2], air[0], air[2], values[2]))
            return *state, *air, *result
        case "constants": return (altitude.USSA76_GEOPOTENTIAL_EARTH_RADIUS_M,
            atmosphere.STANDARD_SEA_LEVEL_TEMPERATURE_K, atmosphere.STANDARD_SEA_LEVEL_PRESSURE_PA,
            atmosphere.STANDARD_GRAVITY_M_S2, atmosphere.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K,
            atmosphere.USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M, atmosphere.USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M,
            air_properties.DRY_AIR_SPECIFIC_HEAT_RATIO, air_properties.SUTHERLAND_BETA, air_properties.SUTHERLAND_CONSTANT_K)
        case "math": return math.exp(values[0]), math.exp(values[0]), math.pow(values[1], values[2]), math.pow(values[1], values[2]), math.sqrt(values[1]), math.sqrt(values[1])
        case "hypot": return (math.hypot(*values[0]),) * 2
        case _: raise AssertionError(operation)


for op in ("orthometric", "ellipsoidal", "agl", "from_agl"):
    for value in (-1000., 0., 1234.5):
        for offset in (-30., 0., 30.):
            add(op, value, offset)
    for bad in (math.nan, math.inf, -math.inf):
        add(op, bad, 0.)
        add(op, 0., bad)
    add(op, sys.float_info.max, (-sys.float_info.max if op in {"orthometric", "agl"} else sys.float_info.max))
for op in ("bridge", "geopotential", "geometric"):
    for value in (-1000., 0., 1000., 11000., 32000., 84852., 86000., 90000., sys.float_info.max, -sys.float_info.max, math.nan, math.inf, -math.inf):
        add(op, value)
add("geopotential", -6356766.)
add("geometric", 6356766.)
heights = set(range(-5000, 84853, 50)) | {84852}
for height in (0., 11000., 20000., 32000., 47000., 51000., 71000., 84852.):
    heights.update((math.nextafter(height, -math.inf), height, math.nextafter(height, math.inf)))
for height in sorted(heights):
    add("chain", height, [3., 4., 12.], .2)
for height in (-5000.0001, math.nextafter(-5000., -math.inf), 84852.0001, 90000., math.nan, math.inf, -math.inf, altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=86000.)):
    add("atmosphere", height)
for t in (186.946, 216.65, 255.65, 270.65, 320.65, 500., sys.float_info.max):
    for rho in (1e-5, .2, 1., 2.):
        add("air", t, math.nan, rho)
for bad in (0., -1., math.nan, math.inf, -math.inf, float.fromhex('0x0.0000000000001p-1022')):
    add("air", bad, 0., 1.)
    add("air", 288., 0., bad)
add("air", -1., 0., math.nan)  # finite checks domain checks'ten önce.
add("gravity")
add("no_wind")
add("constants")
for v in ([0.,0.,0.], [5.,-2.,3.], [0.,-10.,0.], [0.,0.,3.], [500.,0.,0.], [sys.float_info.max,0.,0.]):
    add("wind", v)
for rocket, airmass in (([10,20,30],[0,0,0]), ([0,100,0],[0,20,0]),
    ([0,100,0],[0,-20,0]), ([0,100,0],[10,0,0]), ([10,-20,30],[-5,4,3]),
    ([10,-20,30],[10,-20,30]), ([0,0,0],[0,-10,0]), ([sys.float_info.max,0,0],[-sys.float_info.max,0,0])):
    add("flow", rocket, airmass)
for wrong in ([], [1,2], [1,2,3,4]):
    add("wind", wrong)
    add("flow", wrong, [0,0,0])
    add("flow", [0,0,0], wrong)
    add("conditions", wrong, 2., 260., .00002, .2)
for bad in (math.nan, math.inf, -math.inf):
    for index in range(3):
        v = [0.,0.,0.]
        v[index] = bad
        add("wind", v)
        add("flow", v, [0,0,0])
        add("flow", [0,0,0], v)
        add("conditions", v, 2., 260., .00002, .2)
for index in range(4):
    for bad in (0., -1., math.nan, math.inf, -math.inf):
        prerequisites = [2.,260.,.00002,.2]
        prerequisites[index] = bad
        add("conditions", [3.,4.,12.], *prerequisites)
for v in ([0,0,0], [3,4,12], [-12,3,-4], [6,8,24], [1e-200,0,0], [sys.float_info.max,sys.float_info.max,0]):
    add("conditions", v, 2., 260., .00002, .2)
add("conditions", [0,0,0], 2.,260.,.00002,0.)
add("conditions", [0,0,0], -1.,math.nan,1.,1.)
for prerequisites in ((2.,5e-324,.00002,.2), (2.,260.,5e-324,.2), (2.,260.,.00002,1e308), (1e308,260.,.00002,.2)):
    add("conditions", [3,4,12], *prerequisites)
add("conditions", [1e155,0,0], 1e-100,1e155,1.,1e-155)
add("conditions", [1e-155,0,0], 1e100,1e-155,1e-155,1.)
# Deterministik test-only geniş norm örneklemi; production ayarı veya tolerans değildir.
rng = random.Random(3003)
for _ in range(512):
    v = [rng.uniform(-1000000.,1000000.) for _ in range(3)]
    add("conditions", v, 2.,260.,.00002,.2)
for v in ([3,4,12], [-12,3,-4], [1e155,1e155,1e155], [1e-155,1e-155,1e-155]):
    add("hypot", v)
for values in ((-.2,.75,5.25588), (-2.,.95,-34.1632), (0.,1.,0.), (-8.,.6,17.0816)):
    add("math", *values)
for layer in atmosphere._LAYERS:
    # -3900 m power-law ve 11750 m isothermal ölçülen farklarını ayrıca izole et.
    for delta in (-3900., -.01, 0., 750., 2000.):
        temperature = layer.base_temperature_K + layer.lapse_rate_K_per_m*delta
        exponential = -atmosphere.STANDARD_GRAVITY_M_S2*delta / (
            atmosphere.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K*layer.base_temperature_K)
        power = (-atmosphere.STANDARD_GRAVITY_M_S2 / (
            atmosphere.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K*layer.lapse_rate_K_per_m)
            if layer.lapse_rate_K_per_m else 0.)
        add("math", exponential, temperature/layer.base_temperature_K, power)


def encoded(value):
    if isinstance(value, (list, tuple)):
        return ",".join(encoded(x) for x in value)
    if math.isnan(value): return "NaN"
    if math.isinf(value): return "Infinity" if value > 0 else "-Infinity"
    return repr(float(value))


count_cases("ENVIRONMENT", [(op, tuple(map(encoded, values))) for op, values in cases])
lines = ["\t".join((op, *map(encoded, values))) for op, values in cases]
classpath = os.pathsep.join(str((args.build_dir or CORE / "build") / directory) for directory in ("classes/java/main", "classes/java/test"))
process = subprocess.run([args.java, "-cp", classpath, "org.roketsim.core.EnvironmentParityProbe"],
    input="\n".join(lines)+"\n", text=True, capture_output=True, check=True)
outputs = process.stdout.splitlines()
assert len(outputs) == len(cases), process.stderr
successes = errors = exact_cases = 0
max_relative = {}
max_absolute = {}
norm_exact = norm_total = 0
for (op, values), output in zip(cases, outputs):
    fields = output.split("\t")
    try:
        with np.errstate(over='ignore'):
            expected = np.asarray(python_call(op, values), dtype=float).reshape(-1)
    except ValueError as error:
        category = type(error).__name__ if type(error) is not ValueError else "IllegalArgumentException"
        assert fields[:2] == ["ERROR", category], (op, values, fields, error)
        if isinstance(error, atmosphere.AtmosphereDomainError):
            assert len(fields) == 6, fields
            assert fields[2] == "geopotential_height_m"
            assert list(map(float, fields[3:])) == [error.requested_geopotential_height_m, error.minimum_geopotential_height_m, error.maximum_geopotential_height_m]
        elif hasattr(error, "field_name"):
            assert len(fields) == 4, fields
            assert fields[2] == error.field_name and float(fields[3]) == error.value
        else:
            assert len(fields) == 3, fields
            # Generic aile korunur; değişken float message biçimi sözleşme değildir.
            token = str(error).split()[0]
            assert token in fields[2], (op, values, token, fields)
        errors += 1
        continue
    assert fields[0] == "OK", (op, values, fields)
    actual = np.array(list(map(float, fields[1:])))
    assert len(actual) == len(expected)
    if op in {"chain", "atmosphere"}:
        tolerances = ([2e-14]*3+[3e-15]*4+[1e-14]*4)[:len(actual)]
    elif op == "air": tolerances = [3e-15]*len(actual)
    elif op in {"conditions", "hypot"}: tolerances = [1e-14]*len(actual)
    elif op == "math": tolerances = [2e-14]*len(actual)
    else: tolerances = [0.]*len(actual)
    # Toleranslar source test_atmosphere/air_properties/basic'ten; yeni threshold yok.
    for index, (want, got, tolerance) in enumerate(zip(expected, actual, tolerances)):
        assert math.isfinite(got)
        assert abs(got-want) <= abs(want)*tolerance, (op,values,index,want,got,tolerance)
        key = f"{op}[{index}]"
        max_absolute[key] = max(max_absolute.get(key,0.),abs(got-want))
        max_relative[key] = max(max_relative.get(key,0.),abs(got-want)/abs(want) if want else 0.)
    if op == "conditions":
        norm_total += 1
        norm_exact += int(actual[0] == expected[0])
    exact_cases += int(np.array_equal(actual, expected))
    if not np.array_equal(actual, expected):
        print("NONEXACT", op, values, "Python", expected.tolist(), "Java", actual.tolist())
    successes += 1
assert successes + errors == len(cases)
print(f"ENVIRONMENT_PARITY: {successes} success, {errors} error cases passed; {exact_cases} numerically exact cases")
print(f"HYPOT3: {norm_exact}/{norm_total} successful condition norms numerically exact")
for prefix in ("chain", "air", "conditions", "math", "hypot"):
    selected = [key for key in max_relative if key.startswith(prefix+'[')]
    print(prefix, "max relative differences", {key:max_relative[key] for key in selected})
    print(prefix, "max absolute differences", {key:max_absolute[key] for key in selected})
print(sys.version)
print("NumPy",np.__version__,platform.platform())
