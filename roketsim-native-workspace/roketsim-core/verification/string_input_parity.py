"""AUD-002 lexical reference cases; no expected values originate from Java."""

import argparse
import base64
import itertools
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import unicodedata

import numpy as np
from vector_string_evidence import CASES, as_vector


def cases():
    values = list(CASES)
    values += [sign + mantissa + exponent for sign, mantissa, exponent in itertools.product(
        ("", "+", "-"), ("0", "00", "1_2", "1.", ".5", "1_2.3_4"),
        ("", "e0", "E-2", "e+1_0", "e_2", "e", "e+-2"))]
    values += [chr(cp) + ".5" for cp in range(sys.maxunicode + 1)
               if unicodedata.category(chr(cp)) == "Nd"]
    whitespace = {chr(cp) for cp in range(sys.maxunicode + 1) if chr(cp).isspace()}
    whitespace.update(chr(cp) for cp in range(33))
    whitespace.update(("\u200b", "\ufeff"))
    for char in sorted(whitespace):
        values.extend((char + "1.5" + char, "1" + char + "2"))
    values += ["1_e2", "1._0", "1_.0", "1e1_", "0x1p0", "0X1.P-1", "1.0f", "1.0D",
               "NAN", "+nan", "-nan", "INF", "infinity", "+infinity", "-infinity",
               "nan(1)", "in_f", "\u22121", "\u00b2", "\u2163"]
    return list(dict.fromkeys(values))


def python_outcome(text):
    try:
        value = float(as_vector([text])[0])
        return dict(status="OK", value=value, hex=value.hex())
    except ValueError as error:
        return dict(status="ERROR", category=type(error).__name__, message=str(error),
                    stage="finite" if "must be finite" in str(error) else "conversion")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", required=True)
    parser.add_argument("--build-dir", type=Path, required=True)
    parser.add_argument("--evidence-only", action="store_true")
    args = parser.parse_args()
    inputs = cases()
    if not inputs:
        raise RuntimeError("Empty lexical case collection")
    cp = os.pathsep.join(str(args.build_dir / path) for path in ("classes/java/main", "classes/java/test"))
    mismatches = 0
    for start in range(0, len(inputs), 48):
        batch = inputs[start:start+48]
        command = [args.java, "-cp", cp, "org.roketsim.core.VectorStringInputProbe",
                   *(base64.b64encode(text.encode()).decode() for text in batch)]
        result = subprocess.run(command, text=True, capture_output=True, check=True)
        lines = result.stdout.splitlines()
        if len(lines) != len(batch):
            raise RuntimeError("Truncated/extra lexical output")
        for text, line in zip(batch, lines):
            expected = python_outcome(text)
            fields = line.split("\t")
            if len(fields) == 2 and fields[0] == "OK":
                value = float(fields[1])
                actual = dict(status="OK", value=value, hex=value.hex())
                match = math.isfinite(value) and expected == actual
            elif len(fields) == 3 and fields[0] == "ERROR":
                message = base64.b64decode(fields[2], validate=True).decode()
                actual = dict(status="ERROR", category=fields[1], message=message,
                              stage="finite" if "must be finite" in message else "conversion")
                match = (expected["status"] == "ERROR" and fields[1] == "IllegalArgumentException"
                         and actual["stage"] == expected["stage"]
                         and message.split(";", 1)[0] == expected["message"].split(";", 1)[0])
            else:
                raise RuntimeError("Invalid lexical output protocol")
            mismatches += int(not match)
            print(json.dumps(dict(input=text, python=expected, java=actual, match=match), allow_nan=False))
    print(json.dumps(dict(total=len(inputs), distinct=len(set(inputs)), mismatches=mismatches,
                          python=sys.version, numpy=np.__version__, unicode=unicodedata.unidata_version)))
    if mismatches and not args.evidence_only:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
