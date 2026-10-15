"""Small test-only output-contract checks shared by migration parity runners."""

if not __debug__:
    raise SystemExit("Parity verification requires assertions; optimized Python is forbidden")


def nonempty(values):
    if not values:
        raise AssertionError("Empty/unexecuted parity case collection")


def count_cases(label, values):
    nonempty(values)
    def canonical(value):
        if hasattr(value, "tolist"):
            return canonical(value.tolist())
        if isinstance(value, dict):
            return tuple((key, canonical(item)) for key, item in sorted(value.items()))
        if isinstance(value, (list, tuple)):
            return tuple(map(canonical, value))
        if isinstance(value, float):
            return "float", value.hex()
        return type(value).__name__, repr(value)
    distinct = len({repr(canonical(value)) for value in values})
    print(f"{label}_INPUTS: total={len(values)} distinct_effective={distinct}")


def keyed_output(parts, expected):
    pairs = [part.split("=", 1) for part in parts]
    if any(len(pair) != 2 for pair in pairs):
        raise AssertionError("Malformed result field")
    keys = [pair[0] for pair in pairs]
    if len(set(keys)) != len(keys):
        raise AssertionError("Duplicate result field")
    if set(keys) != set(expected):
        raise AssertionError(("Missing/unexpected result fields", keys, expected))
    return dict(pairs)
