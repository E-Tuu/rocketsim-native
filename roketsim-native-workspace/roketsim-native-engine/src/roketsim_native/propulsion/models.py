"""NAT-011C.1 immutable SI motor verileri; runtime interpolation/network yoktur.

Thrust curve sonraki dynamics için veridir; certification özeti yalnız V&V
metadata'dır. Geçersiz veri sıralanmaz, düzeltilmez veya normalize edilmez.
"""

from dataclasses import dataclass
from enum import Enum

from roketsim_native.math.numerical import require_finite

__all__ = ("MotorType", "MotorValidationError", "ThrustSample",
           "MotorCertificationReference", "MotorDataProvenance", "MotorDefinition",
           "MotorMassSample", "MotorCgSample")


class MotorType(Enum):
    """SINGLE_USE fiziksel non-reloadable hardware'dir; veri nesnesi tekrar kullanılabilir."""

    SINGLE_USE = "single_use"


class MotorValidationError(ValueError):
    """Finite semantic/physical motor-data hatası için structured bilgi."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class ThrustSample:
    """Tek thrust örneği: s ve N; her iki değer finite ve non-negative."""

    time_s: float
    thrust_N: float

    def __post_init__(self) -> None:
        require_finite(self.time_s, name="time_s")
        require_finite(self.thrust_N, name="thrust_N")
        for name, value, code in (("time_s", self.time_s, "NEGATIVE_THRUST_TIME"),
                                  ("thrust_N", self.thrust_N, "NEGATIVE_THRUST")):
            if value < 0.0:
                raise MotorValidationError(error_code=code, field_name=name, value=value)


@dataclass(frozen=True, slots=True)
class MotorMassSample:
    """Kaynak motor kütle örneği (s, kg); model tarafından üretilmiş fallback değildir."""

    time_s: float
    mass_kg: float

    def __post_init__(self) -> None:
        require_finite(self.time_s, name="time_s")
        require_finite(self.mass_kg, name="mass_kg")
        if self.time_s < 0.0:
            raise MotorValidationError(error_code="NEGATIVE_MASS_SAMPLE_TIME",
                field_name="time_s", value=self.time_s)
        if self.mass_kg <= 0.0:
            raise MotorValidationError(error_code="NON_POSITIVE_MASS_SAMPLE",
                field_name="mass_kg", value=self.mass_kg)


@dataclass(frozen=True, slots=True)
class MotorCgSample:
    """Kaynak CG: motor ön yüzünden arka yüze doğru m; üst sınırı motor tanımı doğrular."""

    time_s: float
    cg_from_front_m: float

    def __post_init__(self) -> None:
        require_finite(self.time_s, name="time_s")
        require_finite(self.cg_from_front_m, name="cg_from_front_m")
        if self.time_s < 0.0:
            raise MotorValidationError(error_code="NEGATIVE_CG_SAMPLE_TIME",
                field_name="time_s", value=self.time_s)
        if self.cg_from_front_m < 0.0:
            raise MotorValidationError(error_code="NEGATIVE_CG_POSITION",
                field_name="cg_from_front_m", value=self.cg_from_front_m)


@dataclass(frozen=True, slots=True)
class MotorCertificationReference:
    """Ölçülmüş static-test V&V metadata; runtime thrust kaynağı değildir."""

    measured_total_impulse_N_s: float
    measured_average_thrust_N: float
    measured_peak_thrust_N: float
    measured_burn_time_s: float

    def __post_init__(self) -> None:
        values = (("measured_total_impulse_N_s", self.measured_total_impulse_N_s),
                  ("measured_average_thrust_N", self.measured_average_thrust_N),
                  ("measured_peak_thrust_N", self.measured_peak_thrust_N),
                  ("measured_burn_time_s", self.measured_burn_time_s))
        for name, value in values:
            require_finite(value, name=name)
        for name, value in values:
            if value <= 0.0:
                raise MotorValidationError(error_code="INVALID_CERTIFICATION_REFERENCE", field_name=name, value=value)


@dataclass(frozen=True, slots=True)
class MotorDataProvenance:
    """Statik traceability metni; URL'ler runtime'da açılmaz."""

    manufacturer_source: str
    certification_source: str
    thrust_curve_source: str
    normalization_note: str
    mass_curve_source: str | None
    cg_curve_source: str | None

    def __post_init__(self) -> None:
        for name in ("manufacturer_source", "certification_source", "thrust_curve_source", "normalization_note"):
            value = getattr(self, name)
            if not isinstance(value, str) or not value.strip():
                raise MotorValidationError(error_code="INVALID_PROVENANCE", field_name=name, value=value)
        for name in ("mass_curve_source", "cg_curve_source"):
            value = getattr(self, name)
            if value is not None and (not isinstance(value, str) or not value.strip()):
                raise MotorValidationError(error_code="INVALID_PROVENANCE", field_name=name, value=value)


@dataclass(frozen=True, slots=True)
class MotorDefinition:
    """Seçilebilir immutable motor tanımı; fit, thrust(t), depletion veya motor CG yok."""

    motor_id: str
    manufacturer: str
    designation: str
    family: str
    motor_type: MotorType
    propellant_name: str
    diameter_m: float
    length_m: float
    initial_mass_kg: float
    propellant_mass_kg: float
    ejection_delay_s: float
    thrust_curve: tuple[ThrustSample, ...]
    certification: MotorCertificationReference
    provenance: MotorDataProvenance
    mass_curve: tuple[MotorMassSample, ...] | None
    cg_curve: tuple[MotorCgSample, ...] | None

    def __post_init__(self) -> None:
        positive_values = (
            ("diameter_m", self.diameter_m, "NON_POSITIVE_MOTOR_DIAMETER"),
            ("length_m", self.length_m, "NON_POSITIVE_MOTOR_LENGTH"),
            ("initial_mass_kg", self.initial_mass_kg, "NON_POSITIVE_INITIAL_MASS"),
            ("propellant_mass_kg", self.propellant_mass_kg, "NON_POSITIVE_PROPELLANT_MASS"),
        )
        for name, value, _ in positive_values:
            require_finite(value, name=name)
        require_finite(self.ejection_delay_s, name="ejection_delay_s")
        for name, code in (("motor_id", "EMPTY_MOTOR_ID"), ("manufacturer", "EMPTY_MANUFACTURER"),
                           ("designation", "EMPTY_DESIGNATION"), ("family", "EMPTY_MOTOR_FAMILY"),
                           ("propellant_name", "EMPTY_PROPELLANT_NAME")):
            value = getattr(self, name)
            if not isinstance(value, str) or not value.strip():
                raise MotorValidationError(error_code=code, field_name=name, value=value)
        if not isinstance(self.motor_type, MotorType):
            raise MotorValidationError(error_code="INVALID_MOTOR_TYPE", field_name="motor_type", value=self.motor_type)
        for name, value, code in positive_values:
            if value <= 0.0:
                raise MotorValidationError(error_code=code, field_name=name, value=value)
        if self.propellant_mass_kg >= self.initial_mass_kg:
            raise MotorValidationError(error_code="PROPELLANT_MASS_NOT_LESS_THAN_INITIAL_MASS",
                field_name="propellant_mass_kg", value=self.propellant_mass_kg)
        if self.ejection_delay_s < 0.0:
            raise MotorValidationError(error_code="NEGATIVE_EJECTION_DELAY", field_name="ejection_delay_s", value=self.ejection_delay_s)
        if not isinstance(self.certification, MotorCertificationReference):
            raise MotorValidationError(error_code="INVALID_CERTIFICATION_REFERENCE", field_name="certification", value=self.certification)
        if not isinstance(self.provenance, MotorDataProvenance):
            raise MotorValidationError(error_code="INVALID_PROVENANCE", field_name="provenance", value=self.provenance)
        curve = self.thrust_curve
        if not isinstance(curve, tuple) or not all(isinstance(s, ThrustSample) for s in curve):
            raise MotorValidationError(error_code="INVALID_THRUST_CURVE_TYPE", field_name="thrust_curve", value=curve)
        if len(curve) < 3:
            raise MotorValidationError(error_code="TOO_FEW_THRUST_SAMPLES", field_name="thrust_curve", value=len(curve))
        if curve[0].time_s != 0.0 or curve[0].thrust_N != 0.0:
            raise MotorValidationError(error_code="INVALID_THRUST_CURVE_START", field_name="thrust_curve", value=curve[0])
        for previous, current in zip(curve, curve[1:]):
            if current.time_s <= previous.time_s:
                raise MotorValidationError(error_code="NON_INCREASING_THRUST_TIME", field_name="time_s", value=current.time_s)
        if not any(s.thrust_N > 0.0 for s in curve):
            raise MotorValidationError(error_code="NO_POSITIVE_THRUST", field_name="thrust_curve", value=curve)
        if curve[-1].time_s <= 0.0 or curve[-1].thrust_N != 0.0:
            raise MotorValidationError(error_code="INVALID_THRUST_CURVE_END", field_name="thrust_curve", value=curve[-1])

        # Kaynak eğrileri bağımsız zaman grid'leridir; None model seçimi yapmaz.
        for name, samples, sample_type, source in (
            ("mass", self.mass_curve, MotorMassSample, self.provenance.mass_curve_source),
            ("cg", self.cg_curve, MotorCgSample, self.provenance.cg_curve_source),
        ):
            code = name.upper()
            field = f"{name}_curve"
            if (samples is None) != (source is None):
                raise MotorValidationError(error_code=f"{code}_CURVE_PROVENANCE_MISMATCH",
                    field_name=field, value=samples)
            if samples is None:
                continue
            if not isinstance(samples, tuple) or not all(isinstance(s, sample_type) for s in samples):
                raise MotorValidationError(error_code=f"INVALID_{code}_CURVE_TYPE",
                    field_name=field, value=samples)
            if len(samples) < 2:
                raise MotorValidationError(error_code=f"TOO_FEW_{code}_SAMPLES",
                    field_name=field, value=len(samples))
            if samples[0].time_s != 0.0:
                raise MotorValidationError(error_code=f"INVALID_{code}_CURVE_START",
                    field_name=field, value=samples[0].time_s)
            if any(b.time_s <= a.time_s for a, b in zip(samples, samples[1:])):
                raise MotorValidationError(error_code=f"NON_INCREASING_{code}_CURVE_TIME",
                    field_name=field, value=samples)
            if samples[-1].time_s < curve[-1].time_s:
                raise MotorValidationError(error_code=f"{code}_CURVE_INSUFFICIENT_COVERAGE",
                    field_name=field, value=samples[-1].time_s)
        if self.mass_curve is not None:
            if self.mass_curve[0].mass_kg != self.initial_mass_kg:
                raise MotorValidationError(error_code="MASS_CURVE_INITIAL_MISMATCH",
                    field_name="mass_curve", value=self.mass_curve[0].mass_kg)
            if any(b.mass_kg > a.mass_kg for a, b in zip(self.mass_curve, self.mass_curve[1:])):
                raise MotorValidationError(error_code="MASS_CURVE_INCREASES",
                    field_name="mass_curve", value=self.mass_curve)
        if self.cg_curve is not None:
            for sample in self.cg_curve:
                if not 0.0 <= sample.cg_from_front_m <= self.length_m:
                    raise MotorValidationError(error_code="CG_OUTSIDE_MOTOR",
                        field_name="cg_from_front_m", value=sample.cg_from_front_m)
