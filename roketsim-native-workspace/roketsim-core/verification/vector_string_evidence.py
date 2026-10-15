"""AUD-002 before/after evidence from the installed reference and actual Java API."""

import argparse
import base64
import json
import os
import platform
from pathlib import Path
import subprocess
import sys

import numpy as np

CORE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CORE.parent / "roketsim-native-engine/src"))
from roketsim_native.math.vectors import as_vector

CASES = ["1_0", "1f", "0x1.0p0", "1", "-0.0", ".5", "1.", "1e-3",
         "1_2.3_4e+0_2", "_1", "1_", "1__0", "1e_2", "1d", "0x10",
         " 1.5 \t\n", "", " ", "NaN", "Infinity", "-Infinity",
         "1e309", "1e-400", "4.9406564584124654e-324", "1.7976931348623157e308",
         "inf", "+INF", "nan", "-NaN", "1F", "1D", "\u0661_\u0662.\u0663",
         "\uff11\uff12.\uff15", "\u00a01.5\u00a0", "\u00851.5\u0085",
         "\u001c1.5\u001c", "\x001.5\x00", "\u20031.5\u2003", "1\u00a02"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", required=True)
    parser.add_argument("--build-dir", type=Path, required=True)
    args = parser.parse_args()
    cp = os.pathsep.join(str(args.build_dir / path) for path in ("classes/java/main", "classes/java/test"))
    command = [args.java, "-cp", cp, "org.roketsim.core.VectorStringInputProbe",
               *(base64.b64encode(text.encode()).decode() for text in CASES)]
    process = subprocess.run(command, text=True, capture_output=True, check=True)
    print(json.dumps(dict(python=sys.version, numpy=np.__version__, platform=platform.platform(),
                         command=command, java_exit_code=process.returncode)))
    lines = process.stdout.splitlines()
    if len(lines) != len(CASES):
        raise RuntimeError("Incorrect Java output length")
    for text, line in zip(CASES, lines):
        try:
            value = float(as_vector([text])[0])
            expected = dict(status="OK", value=value)
        except ValueError as error:
            expected = dict(status="ERROR", category=type(error).__name__, message=str(error))
        parts = line.split("\t")
        actual = (dict(status="OK", value=float(parts[1])) if parts[0] == "OK" else
                  dict(status="ERROR", category=parts[1], message=base64.b64decode(parts[2]).decode()))
        print(json.dumps(dict(input=text, python=expected, java=actual), ensure_ascii=True, allow_nan=False))


if __name__ == "__main__":
    main()
