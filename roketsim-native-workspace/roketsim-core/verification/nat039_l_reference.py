"""Independent scalar L/r1 verification. Qualification precedes native-data input."""

import argparse
from bisect import bisect_left
from dataclasses import dataclass
from decimal import Decimal as D, localcontext
import hashlib
import json
import math
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

import numpy as np
import scipy
from scipy.integrate import solve_ivp


ENG_HASH = "756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd"
G = 9.80665
R = 287.053
AREA = math.pi * .08**2
LENGTH = .32 + 1.60
LEVELS = ((1e-9, 1e-11, .1), (1e-11, 1e-13, .05), (2.5e-13, 2.5e-15, .025))
EVENT_NAMES = ("APOGEE", "GROUND")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def insist(condition, message):
    if not condition:
        raise ValueError(message)


@dataclass(frozen=True)
class Curve:
    times: tuple
    forces: tuple
    impulses: tuple
    loaded: float
    propellant: float

    @classmethod
    def read(cls, path):
        insist(sha(path) == ENG_HASH, "Unexpected ENG bytes")
        rows = [line.split() for line in path.read_text(encoding="ascii").splitlines()
                if line.strip() and not line.lstrip().startswith(";")]
        insist(rows[0] == ["TFL1256WS", "75", "530.6", "-", "1.905", "3.905", "Teknofest26"],
               "Unexpected ENG header")
        nodes = [(D(0), D(0)), *(tuple(map(D, row)) for row in rows[1:])]
        with localcontext() as ctx:
            ctx.prec = 50
            impulse = [D(0)]
            for (a, fa), (b, fb) in zip(nodes, nodes[1:]):
                insist(b > a and min(fa, fb) >= 0, "Invalid curve segment")
                impulse.append(impulse[-1] + (b - a) * (fa + fb) / 2)
        insist(nodes[-1] == (D("2.967"), D(0)), "Unexpected curve end")
        return cls(tuple(float(t) for t, _ in nodes), tuple(float(f) for _, f in nodes),
                   tuple(map(float, impulse)), float(rows[0][5]), float(rows[0][4]))

    def at(self, time):
        index = bisect_left(self.times, time)
        if index == len(self.times):
            return 0., self.impulses[-1]
        if time == self.times[index]:
            return self.forces[index], self.impulses[index]
        start = self.times[index - 1]
        dt = time - start
        f0, f1 = self.forces[index - 1:index + 1]
        force = f0 + (f1 - f0) * (dt / (self.times[index] - start))
        return force, self.impulses[index - 1] + dt * (f0 + force) / 2

    def mass(self, impulse):
        return self.loaded - self.propellant * (impulse / self.impulses[-1])


def structure_mass():
    cone = 1800 * AREA * .32 / 3
    shell = 4000 * math.pi * (.08**2 - .077**2) * 1.6
    fins = 3 * 2000 * .003 * (.24 + .12) * .11 / 2
    mount = 2000 * math.pi * (.042**2 - .04**2) * .7
    rings = 2 * 1800 * math.pi * (.077**2 - .042**2) * .004
    return math.fsum((cone, shell, fins, mount, rings))


STRUCTURE = structure_mass()


def air(z):
    h = 1200 + z
    insist(-5000 <= h < 11000, f"L reference left its first-layer contract at {h} m")
    temperature = 288.15 - .0065 * h
    pressure = 101325 * (temperature / 288.15)**(G / (R * .0065))
    density = pressure / (R * temperature)
    sound = math.sqrt(1.4 * R * temperature)
    viscosity = 1.458e-6 * temperature**1.5 / (temperature + 110.4)
    return density, sound, viscosity / density


def aero(z, v):
    density, sound, nu = air(z)
    # Frozen L has zero wind, square fins and zero equivalent roughness.
    speed = abs(v)
    mach = speed / sound
    insist(mach <= 1, f"UNSUPPORTED_MACH_REGIME {mach}")
    reynolds = speed * LENGTH / nu
    cf = (1 - .1 * mach**2) / (1.5 * math.log(max(10000, reynolds)) - 5.6)**2
    wetted = math.pi * .08 * (math.hypot(.32, .08) + 2 * 1.6)
    fin_mac = (2 / 3) * (.24**2 + .24 * .12 + .12**2) / (.24 + .12)
    fin_planform = (.24 + .12) * .11 / 2
    friction = cf * ((1 + .16 / (2 * LENGTH)) * wetted
                     + (1 + 2 * .003 / fin_mac) * 6 * fin_planform) / AREA
    sine = .08 / math.hypot(.32, .08)
    c0 = .8 * sine**2
    nose_pressure = c0 + (sine - c0) * mach**(4 / 2.4 * (1 - sine / 2) / (sine - c0))
    edge_ratio = 3 * .003 * .11 / AREA
    fin_pressure = edge_ratio * .85 * (1 + mach**2 / 4 + mach**4 / 40) * (.11**2 / (.11**2 + .07**2))
    base = (.12 + .13 * mach**2) * (1 + edge_ratio)
    cd = math.fsum((friction, nose_pressure, fin_pressure, base))
    return -.5 * density * AREA * cd * speed * v, mach, reynolds


def decimal_air_force(z, v):
    """Separate high-precision anchor using algebraic area ratios and signed pressure."""
    with localcontext() as ctx:
        ctx.prec = 50
        pi = D("3.1415926535897932384626433832795028841971693993751")
        temp = D("288.15") - D(".0065") * (D(1200) + D(z))
        gas = D("287.053")
        rho = D(101325) / (gas * temp) * ((temp / D("288.15")).ln()
              * D("9.80665") / (gas * D(".0065"))).exp()
        sound = (D("1.4") * gas * temp).sqrt()
        mach = abs(D(v)) / sound
        re = abs(D(v)) * D("1.92") * rho * (temp + D("110.4")) / (D("1.458e-6") * temp * temp.sqrt())
        cf = (1 - mach**2 / 10) / (D("1.5") * max(re, D(10000)).ln() - D("5.6"))**2
        slant = D(17).sqrt() * D(".08")
        cone_body_ratio = (slant + D("3.2")) / D(".08")
        mean_chord = D(".18666666666666666666666666666666666666666666666667")
        area = pi * D(".0064")
        friction = cf * (D(25) / 24 * cone_body_ratio
                         + (1 + D(".006") / mean_chord) * D(".1188") / area)
        s = 1 / D(17).sqrt()
        p0 = D(".8") / 17
        exponent = (2 - s) / (D("1.2") * (s - p0))
        nose = p0 if not mach else p0 + (s - p0) * (exponent * mach.ln()).exp()
        edge = D(".00099") / area
        fin = edge * D(".85") * (1 + mach**2 / 4 + mach**4 / 40) * D(121) / 170
        base = (D(".12") + D(".13") * mach**2) * (1 + edge)
        return tuple(map(float, (rho, sound, -(rho * area * D(v) * abs(D(v))) * (friction + nose + fin + base) / 2)))


def rhs(curve):
    def evaluate(t, y):
        z, v = y
        thrust, impulse = curve.at(t)
        drag, _, _ = aero(z, v)
        return v, (thrust + drag) / (STRUCTURE + curve.mass(impulse)) - G
    return evaluate


def event(function, direction):
    function.direction = direction
    function.terminal = True
    return function


def solve(fun, span, state, level, stop=None):
    relative, absolute, step = level
    solution = solve_ivp(fun, span, state, method="DOP853", rtol=relative, atol=absolute,
                         max_step=step, dense_output=True, events=stop)
    insist(solution.success, solution.message)
    if stop:
        insist(len(solution.t_events[0]) == 1, f"Missing root on {span}")
    return solution


def close(label, actual, expected, bound, records):
    error = float(actual - expected)
    records[label] = dict(actual=float(actual), expected=float(expected), signed_error=error, limit=bound)
    insist(abs(error) <= bound, f"{label}: {actual} versus {expected} (limit {bound})")


def anchors(curve):
    records = {}
    # The integrated closed forms are O(1) and use the tightest solver settings.
    # 1e-9 in the reported SI units is the predeclared analytical qualification target.
    bound = 1e-9
    level = LEVELS[-1]
    ballistic = lambda t, y: (y[1], -8.)
    apogee = solve(ballistic, (0., 2.), (0., 5.1), level, event(lambda t, y: y[1], -1))
    ground = solve(ballistic, (.6375, 2.), (1.625625, 0.), level, event(lambda t, y: y[0], -1))
    ballistic_state = apogee.sol(.4)
    close("ballistic z at .4 s [m]", ballistic_state[0], 1.4, bound, records)
    close("ballistic v at .4 s [m/s]", ballistic_state[1], 1.9, bound, records)
    close("ballistic apogee [s]", apogee.t[-1], .6375, bound, records)
    close("ballistic apogee [m]", apogee.y[0, -1], 1.625625, bound, records)
    close("ballistic ground [s]", ground.t[-1], 1.275, bound, records)
    close("ballistic ground [m/s]", ground.y[1, -1], -5.1, bound, records)

    # v <= 0, constant g and constant drag-per-mass k.
    # v=-sqrt(g/k)*tanh(sqrt(g*k)*t), z=z0-log(cosh(sqrt(g*k)*t))/k.
    k, g, start = .2, 8., 3.
    elapsed = math.acosh(math.exp(k * start)) / math.sqrt(g * k)
    descent = solve(lambda t, y: (y[1], -g - k * y[1] * abs(y[1])),
                    (0., 2.), (start, 0.), level, event(lambda t, y: y[0], -1))
    s = .25
    close("quadratic drag z at .25 s [m]", descent.sol(s)[0],
          start - math.log(math.cosh(math.sqrt(g*k)*s)) / k, bound, records)
    close("quadratic drag ground [s]", descent.t[-1], elapsed, bound, records)
    close("quadratic drag ground [m/s]", descent.y[1, -1],
          -math.sqrt(g/k) * math.tanh(math.sqrt(g*k)*elapsed), bound, records)

    for t, f in zip(curve.times, curve.forces):
        close(f"ENG knot {t} s [N]", curve.at(t)[0], f, 0., records)
    expected = {"total impulse [N s]": (curve.impulses[-1], 3650.044730405055),
                "loaded motor mass [kg]": (curve.mass(0), 3.905),
                "intermediate motor mass [kg]": (curve.mass(curve.at(1.4835)[1]), 2.818710177158778),
                "depleted motor mass [kg]": (curve.mass(curve.impulses[-1]), 2.),
                "structure mass [kg]": (STRUCTURE, 14.5965357819681),
                "initial total mass [kg]": (STRUCTURE + curve.loaded, 18.5015357819681),
                "depleted total mass [kg]": (STRUCTURE + curve.mass(curve.impulses[-1]), 16.5965357819681)}
    for label, (actual, target) in expected.items():
        close(label, actual, target, 128 * math.ulp(target), records)
    for z, v in ((0, 20), (500, -100), (1000, 0)):
        a, c, _ = air(z)
        drag, _, _ = aero(z, v)
        for key, actual, target in zip(("rho [kg/m3]", "sound [m/s]", "drag [N]"),
                                       (a, c, drag), decimal_air_force(z, v)):
            close(f"z={z} v={v} {key}", actual, target, 256 * math.ulp(target), records)
    return records


@dataclass
class Flight:
    curve: Curve
    segments: list
    events: dict
    diagnostics: dict

    def state(self, t):
        for segment in self.segments:
            if t <= segment.t[-1]:
                return segment.sol(t)
        raise ValueError(f"Reference requested past ground at {t}")


def flight(curve, level):
    fun = rhs(curve)
    segments = []
    events = {}
    diagnostics = {}
    start, state = 0., (0., 20.)
    for knot in curve.times[1:]:
        segment = solve(fun, (start, knot), state, level)
        segments.append(segment)
        start, state = knot, segment.y[:, -1]
    events["BURNOUT"] = (start, *map(float, state))
    for label, fn, direction in (
        ("LOW_RE_ASCENT", lambda t, y: aero(*y)[2] - 10000, -1),
        ("APOGEE", lambda t, y: y[1], -1),
        ("LOW_RE_DESCENT", lambda t, y: aero(*y)[2] - 10000, 1),
        ("GROUND", lambda t, y: y[0], -1),
    ):
        segment = solve(fun, (start, 100.), state, level, event(fn, direction))
        segments.append(segment)
        start, state = float(segment.t[-1]), segment.y[:, -1]
        events[label] = (start, *map(float, state))
        diagnostics[label] = {"last_step_start_s": float(segment.t[-2]), "root_s": start,
                              "root_residual": float(fn(start, state))}
    return Flight(curve, segments, events, diagnostics)


def pack(flight_result):
    return dict(events=flight_result.events, root_diagnostics=flight_result.diagnostics,
                checkpoints={str(t): flight_result.state(t).tolist() for t in (1.340, 2.967, 10.)},
                accepted_reference_steps=sum(len(s.t)-1 for s in flight_result.segments),
                max_reference_endpoint_mach=max(aero(z, v)[1] for s in flight_result.segments for z, v in s.y.T))


def qualify(curve):
    analytical = anchors(curve)
    flights = [flight(curve, level) for level in LEVELS]
    reference = [pack(f) for f in flights]
    # Guard reference convergence independently of the Java residuals. These are
    # reference-repeatability targets, not native acceptance limits.
    stability = {}
    for name in EVENT_NAMES:
        differences = [np.abs(np.subtract(b["events"][name], a["events"][name]))
                       for a, b in zip(reference, reference[1:])]
        insist(np.max(differences[-1]) < 1e-7, f"Unstable reference event {name}: {differences[-1]}")
        stability[name] = [d.tolist() for d in differences]
    for t in (1.340, 2.967, 10.):
        differences = [np.abs(b.state(t) - a.state(t)) for a, b in zip(flights, flights[1:])]
        insist(np.max(differences[-1]) < 1e-7, f"Unstable reference checkpoint {t}: {differences[-1]}")
        stability[str(t)] = [d.tolist() for d in differences]
    return flights, dict(classification="QUALIFIED_EMPIRICAL_REFERENCE", native_criterion="REPORTING_ONLY",
                         configuration="n37-03c:L:r1", eng_sha256=ENG_HASH, reference_source_sha256=sha(Path(__file__)),
                         environment={"python": sys.version, "numpy": np.__version__, "scipy": scipy.__version__,
                                      "method": "DOP853"}, analytical_checks=analytical, levels=LEVELS,
                         trajectories=reference, absolute_adjacent_level_differences=stability)


SAMPLE = re.compile(r"^B1_SAMPLE t=(\S+).* z=\[([^]]+)\] v=\[([^]]+)\]")
OCCURRENCE = re.compile(r"eventType=(\w+), timeS=([^,]+), interpolationFraction=([^,]+), "
                        r"estimatedState=TranslationalState3DOF\[positionWorldM=\[([^]]+)\], velocityWorldMS=\[([^]]+)\]")


def vector(text):
    return tuple(map(float, text.split(", ")))


def native_trace(path):
    suite = ET.parse(path).getroot()
    insist(suite.attrib.get("failures") == suite.attrib.get("errors") == suite.attrib.get("skipped") == "0",
           "B1 source report is not a passing trace")
    cases = {}
    current = None
    for line in suite.findtext("system-out").splitlines():
        if line.startswith("B1_RUN "):
            match = re.match(r"B1_RUN L/DIRECT/h=(0\.02|0\.01) id=n37-03c:L:r1 outcome=GROUND "
                             r"samples=(\d+) acceptedSegments=(\d+) .* alignment=ALIGN_MOTOR_SOURCE_BOUNDARIES "
                             r"failure=Optional\.empty$", line)
            current = None
            if match:
                step, count, segments = match.groups()
                insist(step not in cases and int(count) == 1 + int(segments), "Unexpected native trace identity/count")
                current = cases[step] = dict(count=int(count), samples=[], events={})
        elif current is not None and line.startswith("B1_EVENTS"):
            current["events"] = {label: (float(t), float(frac), vector(p)[2], vector(v)[2])
                                 for label, t, frac, p, v in OCCURRENCE.findall(line)}
        elif current is not None and (match := SAMPLE.match(line)):
            t, p, v = match.groups()
            p, v = vector(p), vector(v)
            insist(p[:2] == v[:2] == (0., 0.), "Nonvertical native sample")
            current["samples"].append((float(t), p[2], v[2]))
    insist(set(cases) == {"0.02", "0.01"}, "Missing L trace")
    for case in cases.values():
        s = case["samples"]
        insist(len(s) == case["count"] and s[0] == (0., 0., 20.), "Unexpected native start or count")
        insist(all(a[0] < b[0] for a, b in zip(s, s[1:])), "Nonadvancing native timestamp")
        insist(set(case["events"]) == {"BURNOUT", "APOGEE", "GROUND"}, "Unexpected event set")
    return cases


def uncertainty(values):
    # Conservative spread of all three levels, plus a small output-rounding floor.
    # This is a reproducibility indicator, not a certified enclosure.
    return max(values) - min(values) + 8 * math.ulp(values[-1])


def compare(flights, trace, snapshots):
    result = []
    reference = flights[-1]
    snapshots = snapshots.read_text(encoding="utf-8").splitlines()
    for step, case in trace.items():
        samples, events = case["samples"], case["events"]
        supplemental = {}
        for line in snapshots:
            parts = line.split("\t")
            if parts[:2] == ["NAT039_SNAPSHOT", step]:
                t, z, v, thrust, impulse, mass = map(float, parts[2:])
                insist(t not in supplemental, f"Duplicate export {step} {t}")
                supplemental[t] = (z, v, thrust, impulse, mass)
        insist(set(supplemental) == {1.340, 2.967}, f"Missing native force/mass export at {step}")
        for label, t in (("powered", 1.340), ("burnout", 2.967),
                         ("coast", next(s[0] for s in samples if s[0] >= 10.))):
            match = next((s for s in samples if s[0] == t), None)
            insist(match is not None, f"Missing exact native endpoint {step} {t}")
            _, z, v = match
            values = dict(z_m=(z, [f.state(t)[0] for f in flights]),
                          vz_m_s=(v, [f.state(t)[1] for f in flights]))
            if t in supplemental:
                z_export, v_export, thrust, impulse, mass = supplemental[t]
                insist((z_export, v_export) == (z, v), f"Replay does not match saved state {step} {t}")
                thrust_ref, impulse_ref = reference.curve.at(t)
                total = STRUCTURE + reference.curve.mass(impulse_ref)
                values["mass_kg"] = (mass, [total] * 3)
                if label == "powered":
                    values["thrust_N"] = (thrust, [thrust_ref] * 3)
                    values["impulse_N_s"] = (impulse, [impulse_ref] * 3)
                else:
                    values["time_s"] = (events["BURNOUT"][0], [t] * 3)
            result.extend(rows(step, label, t, values, deterministic=label != "coast"))
        for name in EVENT_NAMES:
            t, fraction, z, v = events[name]
            key, col, state = ("z_m", 1, z) if name == "APOGEE" else ("vz_m_s", 2, v)
            event_rows = rows(step, name.lower(), t,
                              {"time_s": (t, [f.events[name][0] for f in flights]),
                               key: (state, [f.events[name][col] for f in flights])})
            decomposition = {"status": "UNAVAILABLE", "reason": "Rejected ground endpoint absent from the saved history"}
            if name == "APOGEE":
                pair = next(((a, b) for a, b in zip(samples, samples[1:]) if a[2] > 0 >= b[2]), None)
                insist(pair is not None, "Native apogee bracket unavailable")
                a, b = pair
                insist(a[0] <= t <= b[0] and abs(a[2] / (a[2] - b[2]) - fraction) < 8 * math.ulp(fraction),
                       "Saved apogee is inconsistent with accepted bracket")
                za, va = reference.state(a[0])
                zb, vb = reference.state(b[0])
                if va > 0 >= vb:
                    alpha = va / (va - vb)
                    chord_t = a[0] + alpha * (b[0] - a[0])
                    chord_z = za + alpha * (zb - za)
                    decomposition = dict(status="AVAILABLE", bracket_s=[a[0], b[0]], reference_chord_s=chord_t,
                                         reference_chord_z_m=chord_z, interpolation_time_s=chord_t-reference.events[name][0],
                                         interpolation_height_m=chord_z-reference.events[name][1],
                                         remaining_time_s=t-chord_t, remaining_height_m=z-chord_z)
                else:
                    decomposition = dict(status="UNAVAILABLE", reason="Reference apex outside native bracket",
                                         bracket_s=[a[0], b[0]], reference_vz_m_s=[float(va), float(vb)])
            for row in event_rows:
                row["chord_decomposition"] = decomposition
            result.extend(event_rows)
    return result


def rows(step, label, time, values, deterministic=False):
    records = []
    for quantity, (native, levels) in values.items():
        reference = float(levels[-1])
        delta = native - reference
        exact_time = label == "burnout" and quantity == "time_s"
        # Structural anchor is independently tabulated and finite-precision.
        # This local scalar allowance excludes trajectory and event residuals.
        scalar = deterministic and quantity in ("thrust_N", "impulse_N_s", "mass_kg")
        limit = 128 * math.ulp(reference) if scalar else (0. if exact_time else None)
        u = 8 * math.ulp(reference) if scalar else (0. if exact_time else uncertainty(levels))
        records.append(dict(step_s=float(step), checkpoint=label, native_time_s=time, quantity=quantity,
                            native=native, reference=reference, signed_difference=delta, absolute_difference=abs(delta),
                            reference_uncertainty=u, acceptance_limit=limit,
                            classification=("PASS" if abs(delta) + u <= limit else
                                            "FAIL" if abs(delta) - u > limit else "UNRESOLVED")
                            if limit is not None else "REPORTING_ONLY"))
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eng", required=True, type=Path)
    parser.add_argument("--qualification", required=True, type=Path)
    parser.add_argument("--native", type=Path)
    parser.add_argument("--snapshots", type=Path)
    parser.add_argument("--comparison", type=Path)
    options = parser.parse_args()
    insist(sys.version_info[:3] == (3, 12, 4) and np.__version__ == "2.2.6" and scipy.__version__ == "1.15.3",
           "Use the pinned NAT-039 environment")
    if any((options.native, options.snapshots, options.comparison)):
        insist(all((options.native, options.snapshots, options.comparison)) and options.qualification.is_file(),
               "Comparison requires preserved qualification, native trace, snapshots and output")
    flights, qualification = qualify(Curve.read(options.eng))
    document = json.dumps(qualification, indent=2, allow_nan=False) + "\n"
    if options.comparison is None:
        with options.qualification.open("x", encoding="utf-8") as output:
            output.write(document)
        print(f"QUALIFIED {len(qualification['analytical_checks'])} analytical checks; {len(flights)} levels")
    else:
        insist(options.qualification.read_text(encoding="utf-8") == document,
               "Qualification must be preserved and reproducible before native comparison")
        report = dict(qualification_sha256=sha(options.qualification), native_trace_sha256=sha(options.native),
                      snapshots_sha256=sha(options.snapshots),
                      rows=compare(flights, native_trace(options.native), options.snapshots))
        with options.comparison.open("x", encoding="utf-8") as output:
            json.dump(report, output, indent=2, allow_nan=False)
            output.write("\n")
        print(f"COMPARED {len(report['rows'])} quantities")


if __name__ == "__main__":
    main()
