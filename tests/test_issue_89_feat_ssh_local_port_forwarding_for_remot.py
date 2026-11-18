"""Issue #89 acceptance entry point; execute the issue-scoped JVM socket/lifecycle tests.

The Kotlin companion belongs in Android's unit-test source set so the coordinator's
normal ./gradlew test gate also runs it. No shared/frozen test file is modified.
Run directly with Python unittest, or collect this file with pytest.
"""
import pathlib
import subprocess
import unittest


class Issue89ForwardingAcceptance(unittest.TestCase):
    def test_configuration_collision_lifecycle_connection_failure_and_cleanup(self):
        root = pathlib.Path(__file__).resolve().parents[1]
        result = subprocess.run(
            [str(root / "gradlew"), ":app:testDebugUnitTest", "--no-daemon",
             "--tests", "de.fgna.pocketdev.ssh.Issue89LocalPortForwardTest"],
            cwd=root,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=300,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout[-12000:])


if __name__ == "__main__":
    unittest.main()
