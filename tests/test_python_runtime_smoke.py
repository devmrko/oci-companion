"""FDK protocol regression checks; actual server startup is tested in the image pipeline."""
import contextlib
import io
import json
from pathlib import Path
import runpy
import unittest
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).resolve().parents[1] / "src/main/resources/fn-python-template/smoke_runtime.py"

class RuntimeSmokeTest(unittest.TestCase):
    def execute(self, status=200, contract="python-v1", arch="x86_64"):
        response = Mock(status=status)
        response.read.return_value = json.dumps({"contract": contract, "arch": arch}).encode()
        with patch("http.client.HTTPConnection.request") as request, patch("http.client.HTTPConnection.getresponse", return_value=response), patch("sys.argv", [str(SCRIPT), "x86_64"]), contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(SCRIPT), run_name="__main__")
        method, route, body, headers = request.call_args.args
        self.assertEqual((method, route), ("POST", "/call"))
        self.assertEqual(json.loads(body), {"action": "probe"})
        self.assertIn("Fn-Call-Id", headers)
        self.assertIn("Fn-Deadline", headers)

    def test_registered_fdk_route_and_probe_contract(self):
        self.execute()

    def test_http_error_does_not_pass(self):
        with self.assertRaises(AssertionError):
            self.execute(status=404)

    def test_wrong_contract_does_not_pass(self):
        with self.assertRaises(AssertionError):
            self.execute(contract="maven-v1")

    def test_wrong_architecture_does_not_pass(self):
        with self.assertRaises(AssertionError):
            self.execute(arch="aarch64")

if __name__ == "__main__":
    unittest.main()
