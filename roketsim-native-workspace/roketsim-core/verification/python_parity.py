"""J-001 test-only gerçek Python/Java karşılaştırması; production dependency değildir."""

import argparse
import os
import platform
import subprocess
import sys
from pathlib import Path

import numpy as np
import pytest

ENGINE = Path(__file__).resolve().parents[2] / "roketsim-native-engine"
sys.path.insert(0, str(ENGINE / "src"))
from roketsim_native.math.vectors import magnitude, dot, normalize, cross3

parser = argparse.ArgumentParser()
parser.add_argument("--java", required=True)
args = parser.parse_args()
core = Path(__file__).resolve().parents[1]
classpath = os.pathsep.join((str(core / "build/classes/java/main"), str(core / "build/classes/java/test")))
cases = [([3.,4.,0.],[1.,0.,0.]),([1.,2.,3.],[4.,-5.,6.]),
         ([1.,2.],[4.,-5.]),([1.,2.,3.,4.],[4.,3.,2.,1.]),
         ([-2.,0.,5.],[3.,7.,-1.]),([1e-9,0.,0.],[2.,0.,0.])]
for left, right in cases:
    output = subprocess.check_output([args.java,"-cp",classpath,"org.roketsim.core.ParityProbe",
        ",".join(map(str,left)),",".join(map(str,right))],text=True)
    actual = np.array([float(value) for value in output.splitlines()])
    # Mevcut test_vectors.py: scalar pytest.approx; array assert_allclose varsayılanları.
    assert actual[0] == pytest.approx(magnitude(left))
    assert actual[1] == pytest.approx(dot(left,right))
    np.testing.assert_allclose(actual[2:2+len(left)],normalize(left,atol=0.))
    if len(left)==3:
        np.testing.assert_array_equal(actual[2+len(left):],cross3(left,right))
print(f"PARITY: {len(cases)} cases passed")
print(sys.version)
print("NumPy",np.__version__,platform.platform())
