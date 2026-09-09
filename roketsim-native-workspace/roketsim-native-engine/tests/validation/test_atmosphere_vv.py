"""NAT-009E: offline standard, integration, PDAS ve parity evidence ayrı tutulur."""

from decimal import Decimal, localcontext
import math
from pathlib import Path

import pytest

from roketsim_native.environment import altitude
from roketsim_native.environment.atmosphere import USStandardAtmosphere1976Lower, AtmosphereDomainError
from roketsim_native.environment.air_properties import DryAirPropertiesCalculator


PROVIDER = USStandardAtmosphere1976Lower()
CALCULATOR = DryAirPropertiesCalculator()
KNOTS = (0, 11000, 20000, 32000, 47000, 51000, 71000, 84852)
SWEEP = tuple(sorted(set(range(-5000, 84853, 50)) | set(KNOTS) | {-5000, 84852}))
REPORT = (
    Path(__file__).resolve().parents[3]
    / "docs/archive/nat/nat-009e-atmosphere-vv.md"
)
# SOURCE_TABLE: PDAS BigTables Tables 1/2, geometric km. 2026-09-06 doğrulandı.
# Frozen offline veri: T,p,rho,a,mu,nu; testler network kullanmaz.
PDAS = (
    (0, 288.150, 1.0132e5, 1.2250, 340.29, 1.7894e-5, 1.4607e-5),
    (5, 255.676, 5.4048e4, .73643, 320.55, 1.6282e-5, 2.2110e-5),
    (10, 223.252, 2.6500e4, .41351, 299.53, 1.4577e-5, 3.5250e-5),
    (15, 216.650, 1.2112e4, .19476, 295.07, 1.4216e-5, 7.2993e-5),
    (20, 216.650, 5.5293e3, .088910, 295.07, 1.4216e-5, 1.5989e-4),
    (50, 270.650, 7.9779e1, .0010269, 329.80, 1.7037e-5, 1.6590e-2),
    (70, 219.585, 5.2209, 8.2829e-5, 297.06, 1.4377e-5, .17357),
    (80, 198.639, 1.0525, 1.8458e-5, 282.54, 1.3208e-5, .71556),
    (85, 188.893, .44568, 8.2195e-6, 275.52, 1.2647e-5, 1.5386),
)
# Frozen NAT-009C reference knot values; p tolerance = son basamağın yarısı.
KNOT_REFERENCES = (
    (0, 288.150, 101325., 0.), (11000, 216.650, 22632.05546, 5e-6),
    (20000, 216.650, 5474.884660, 5e-7), (32000, 228.650, 868.017648, 5e-7),
    (47000, 270.650, 110.906116, 5e-7), (51000, 270.650, 66.9387501, 5e-8),
    (71000, 214.650, 3.95641035, 5e-9), (84852, 186.946, .373382417, 5e-10),
)


def evaluate_height(geopotential_height_m):
    """C -> D public API zincirinin altı sonucunu döndür."""
    state = PROVIDER.evaluate(geopotential_height_m=geopotential_height_m)
    air = CALCULATOR.evaluate(atmosphere_state=state)
    return (state.temperature_K, state.pressure_Pa, state.density_kg_m3,
            air.speed_of_sound_m_s, air.dynamic_viscosity_Pa_s, air.kinematic_viscosity_m2_s)


def chain(orthometric_height_m):
    """B semantic bridge -> B geopotential -> C -> D."""
    geometric = altitude.atmospheric_geometric_from_orthometric(orthometric_height_m=orthometric_height_m)
    geopotential = altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=geometric)
    return geopotential, evaluate_height(geopotential)


def analytic_reference(geopotential_height_m):
    """ANALYTIC_NUMERICAL: bağımsız 50-digit log-pressure integrali ve air equations."""
    with localcontext() as context:
        context.prec = 50
        requested = Decimal(str(geopotential_height_m))
        temperature, base, log_ratio = Decimal('288.15'), Decimal(0), Decimal(0)
        gas, gravity = Decimal('287.053'), Decimal('9.80665')
        for top, lapse in zip(KNOTS[1:], ('-.0065', '0', '.001', '.0028', '0', '-.0028', '-.002')):
            endpoint = min(requested, Decimal(top))
            lapse = Decimal(lapse)
            next_temperature = temperature + lapse * (endpoint - base)
            log_ratio -= (gravity / gas / lapse * (next_temperature / temperature).ln()
                          if lapse else gravity / gas * (endpoint - base) / temperature)
            temperature = next_temperature
            if requested <= top:
                break
            base = Decimal(top)
        pressure = Decimal(101325) * log_ratio.exp()
        density = pressure / (gas * temperature)
        sound = (Decimal('1.4') * gas * temperature).sqrt()
        viscosity = Decimal('1.458e-6') * temperature ** Decimal('1.5') / (temperature + Decimal('110.4'))
        return tuple(float(x) for x in (temperature, pressure, density, sound, viscosity, viscosity / density))


def assert_analytic(height, values):
    """Yalnız test numerical toleransı; source/parity toleransı değildir."""
    assert values == pytest.approx(analytic_reference(height), rel=2e-14, abs=0)


@pytest.fixture(scope='module')
def sweep_values():
    """Deterministik 50 m grid, bütün knots ve exact endpoint'ler."""
    return tuple(evaluate_height(point) for point in SWEEP)


def test_sea_level_chain():
    """ATM-VV-T01: Orthometric sıfırdan altı standard quantity."""
    height, values = chain(0.)
    assert height == 0.
    assert_analytic(height, values)


@pytest.mark.parametrize('height,temperature,pressure,pressure_atol', KNOT_REFERENCES)
def test_knots(height, temperature, pressure, pressure_atol):
    """ATM-VV-T02: Static frozen knot referansı, display precision ayrı."""
    values = evaluate_height(height)
    assert values[0] == pytest.approx(temperature, rel=0, abs=1e-12)
    assert values[1] == pytest.approx(pressure, rel=0, abs=pressure_atol)
    # SOURCE_TABLE: NASA/TM-2005-213659 seven-layer tablosu, p son basamak 0.01 Pa.
    nasa_pressure = {0: 101325., 11000: 22632.06, 20000: 5474.89,
                     32000: 868.02, 47000: 110.91, 51000: 66.94, 71000: 3.96}
    if height in nasa_pressure:
        assert values[1] == pytest.approx(nasa_pressure[height], rel=0, abs=.01 if height else 0)
    assert_analytic(height, values)


def test_lower_endpoint():
    """ATM-VV-T03: Lower endpoint tüm altı quantity için referansla uyumlu."""
    assert_analytic(-5000, evaluate_height(-5000))


def test_upper_endpoint():
    """ATM-VV-T04: Exact inverse geometric fixture üst sınıra başarıyla döner."""
    geometric = altitude.geometric_from_geopotential(geopotential_height_m=84852.)
    height, values = chain(geometric)
    assert height == pytest.approx(84852., abs=2e-11, rel=0)
    assert_analytic(height, values)


@pytest.mark.parametrize('geometric', [-1000., 1000., 10000., 50000., 85000.])
def test_coordinate_integration(geometric):
    """ATM-VV-T05: Geometric -> H -> C/D ve inverse coordinate consistency."""
    height, values = chain(geometric)
    assert altitude.geometric_from_geopotential(geopotential_height_m=height) == pytest.approx(geometric, rel=2e-14, abs=0)
    assert height == pytest.approx(6356766. * geometric / (6356766. + geometric), rel=2e-15, abs=0)
    assert_analytic(height, values)


def test_rounded_boundary():
    """ATM-VV-T06: 86 km tam girdi rounded H-domain'in biraz üzerindedir."""
    height = altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=86000.)
    assert height == pytest.approx(84852.04584490575, rel=0, abs=3e-11)
    assert height > 84852.
    assert altitude.geometric_from_geopotential(geopotential_height_m=84852.) == pytest.approx(85999.95290624202, rel=0, abs=3e-11)
    with pytest.raises(AtmosphereDomainError):
        evaluate_height(height)


@pytest.mark.parametrize('orthometric', [-1000., 1234.5, 20000.])
def test_model_bridge(orthometric):
    """ATM-VV-T07: Explicit MODEL-DECISION Z_atm := H_orth bütünleşik kalır."""
    assert altitude.atmospheric_geometric_from_orthometric(orthometric_height_m=orthometric) == orthometric
    height, values = chain(orthometric)
    assert_analytic(height, values)


@pytest.mark.parametrize('orthometric', [5000., 35000., 80000.])
def test_representative_full_chain(orthometric):
    """ATM-VV-T08: Low/mid/high B->C->D altı output referans kontrolü."""
    height, values = chain(orthometric)
    assert_analytic(height, values)


def test_dense_finiteness(sweep_values):
    """ATM-VV-T09: Bütün domain grid'inde altı output finite/positive."""
    assert SWEEP[0] == -5000 and SWEEP[-1] == 84852
    for values in sweep_values:
        assert all(math.isfinite(value) and value > 0 for value in values)


def test_pressure_monotonicity(sweep_values):
    """ATM-VV-T10: Strict decreasing pressure; T için böyle bir kural yok."""
    assert all(a[1] > b[1] for a, b in zip(sweep_values, sweep_values[1:]))


def test_density_monotonicity(sweep_values):
    """ATM-VV-T11: Strict decreasing density."""
    assert all(a[2] > b[2] for a, b in zip(sweep_values, sweep_values[1:]))


@pytest.mark.parametrize('boundary', KNOTS[1:-1])
def test_continuity(boundary):
    """ATM-VV-T12: ±0.01 m fiziksel değişim oracle ile; derivative sürekliliği aranmaz."""
    lower, center, upper = (evaluate_height(boundary + offset) for offset in (-.01, 0., .01))
    for offset, values in zip((-.01, 0., .01), (lower, center, upper)):
        assert_analytic(boundary + offset, values)
    # |L|max=0.0065 K/m; hydrostatic |d ln(p)/dH| <= g/(R*Tmin).
    assert abs(upper[0] - lower[0]) <= .0065 * .02 + 1e-12
    assert 0 < math.log(lower[1] / upper[1]) <= 9.80665 / (287.053 * 186.946) * .02 + 2e-14


def test_ideal_gas(sweep_values):
    """ATM-VV-T13: Dense grid boyunca ideal-gas identity."""
    for temperature, pressure, density, *_ in sweep_values:
        assert pressure / (density * temperature) == pytest.approx(287.053, rel=3e-15, abs=0)


def test_acoustic_identity(sweep_values):
    """ATM-VV-T14: Dense grid boyunca acoustic identity."""
    for temperature, _, _, sound, *_ in sweep_values:
        assert sound**2 / (287.053 * temperature) == pytest.approx(1.4, rel=3e-15, abs=0)


def test_viscosity_identity(sweep_values):
    """ATM-VV-T15: Dense grid boyunca mu/nu = supplied rho."""
    for _, _, density, _, viscosity, kinematic in sweep_values:
        assert viscosity / kinematic == pytest.approx(density, rel=3e-15, abs=0)


@pytest.mark.parametrize('case', PDAS, ids=lambda case: f'PDAS-Z{case[0]}km')
def test_pdas(case):
    """ATM-VV-T16: Independent SOURCE_TABLE; geometric altitude, offline fixture."""
    _, values = chain(case[0] * 1000.)
    for index, (value, expected) in enumerate(zip(values, case[1:])):
        tolerance = dict(rel=0, abs=5e-4) if index == 0 else (
            dict(rel=0, abs=5e-3) if index == 3 else dict(rel=5e-5, abs=0)
        )
        if case[0] == 50 and index == 5:
            # DOCUMENTED_DIFFERENCE: bigtables.py v1.5 TransportRatios/Table2,
            # nu_table = ETAZERO*(mu/MUZERO)/sigma; rho_table=RHOZERO*sigma.
            # Bu test-only source projection'dır; ham Native değer değiştirilmez.
            assert value > expected * (1 + 5e-5)
            normalization = 1.4607e-5 * 1.225 / 1.7894e-5
            assert value * normalization == pytest.approx(expected, **tolerance)
            assert_analytic(chain(case[0] * 1000.)[0], values)
        else:
            assert value == pytest.approx(expected, **tolerance), (case[0], index, value, expected)


@pytest.mark.parametrize('geometric', [-6000., 86000., 90000.])
def test_integrated_no_clamp(geometric):
    """ATM-VV-T17: B matematiksel dönüşümü yapar; C explicit domain hatası verir."""
    height = altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=geometric)
    assert altitude.geometric_from_geopotential(geopotential_height_m=height) == pytest.approx(geometric, rel=2e-14, abs=0)
    with pytest.raises(AtmosphereDomainError) as caught:
        chain(geometric)
    assert caught.value.requested_geopotential_height_m == height


def test_repeatability():
    """ATM-VV-T18: Farklı sorgu sırasından sonra aynı input exact aynı output."""
    expected = {value: chain(value) for value in (0., 10000., 50000., 85000.)}
    for _ in range(3):
        for value in reversed(expected):
            assert chain(value) == expected[value]


def test_openrocket_evidence_status():
    """ATM-VV-T19: Capture yokluğu numeric parity PASS diye gösterilmez."""
    evidence = REPORT.read_text(encoding='utf-8')
    assert 'OPENROCKET PARITY: NOT_CAPTURED' in evidence
    assert 'OR-OPEN-ENV-002 remains open' in evidence


def test_evidence_record():
    """ATM-VV-T20: Kalıcı kayıt provenance/commit/status alanlarını kapsar."""
    evidence = REPORT.read_text(encoding='utf-8')
    for required in ('32e25dd5bb6ac89db7f0861443aa18159dbee99e', 'U.S. Standard Atmosphere 1976',
                     'PDAS BigTables', 'STANDARD_REFERENCE_VV', 'INTEGRATION_VV',
                     'DENSE_DOMAIN_VV', 'BOUNDARY_CONTINUITY', 'INTERNAL_CONSISTENCY',
                     'PDAS:', '84.852', '86 km', 'NAT-009E STANDARD V&V:'):
        assert required in evidence
