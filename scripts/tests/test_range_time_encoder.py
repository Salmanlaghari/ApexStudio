import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("range_time_encoder", Path(__file__).parents[1] / "verify-range-time-encoder.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RangeTimeEncoderTest(unittest.TestCase):
    def apk(self, content):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "app.apk"
        with zipfile.ZipFile(path, "w") as archive:
            if content is not None:
                archive.writestr("lib/arm64-v8a/libavcodec.so", content)
        return path

    def test_requires_packaged_openh264_and_never_claims_device_test(self):
        result = module.verify(self.apk(b"config: --enable-libopenh264\x00libopenh264\x00"))
        self.assertFalse(result["deviceEncodingTested"])
        self.assertEqual("libopenh264", result["libraries"][0]["configuredEncoder"])

    def test_missing_encoder_or_library_fails(self):
        for content in (None, b"--enable-mediacodec libx264"):
            with self.subTest(content=content), self.assertRaises(ValueError):
                module.verify(self.apk(content))


if __name__ == "__main__":
    unittest.main()
