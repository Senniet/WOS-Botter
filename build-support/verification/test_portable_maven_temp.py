"""Windows installer CI must not inherit a Linux-only build-temp override."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class MavenTempPortabilityTest(unittest.TestCase):
    def test_repo_maven_config_does_not_pin_machine_temp_directory(self):
        for name in ('.mvn/maven.config', '.mvn/jvm.config'):
            file = ROOT / name
            if file.exists():
                settings = file.read_text(encoding='utf-8')
                self.assertNotIn('java.io.tmpdir=', settings, name)
                self.assertNotIn('/home/', settings, name)


if __name__ == '__main__':
    unittest.main()
