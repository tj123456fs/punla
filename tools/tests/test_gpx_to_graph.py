import tempfile
import unittest
from pathlib import Path
from gpx_to_graph import convert


class GpxConversionTest(unittest.TestCase):
    def parse(self, body):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "track.gpx"
            file.write_text('<gpx xmlns="http://www.topografix.com/GPX/1/1"><trk>' + body + '</trk></gpx>')
            return convert(str(file), "survey")

    def test_recording_gaps_do_not_create_paths(self):
        result = self.parse('<trkseg><trkpt lat="14" lon="121"/><trkpt lat="14.01" lon="121"/></trkseg>'
                            '<trkseg><trkpt lat="14.03" lon="121"/><trkpt lat="14.04" lon="121"/></trkseg>')
        self.assertEqual(4, len(result["nodes"]))
        self.assertEqual([{"from": "survey_0", "to": "survey_1"}, {"from": "survey_2", "to": "survey_3"}], result["edges"])

    def test_invalid_coordinates_are_rejected(self):
        with self.assertRaises(ValueError):
            self.parse('<trkseg><trkpt lat="nan" lon="121"/></trkseg>')

    def test_empty_track_is_rejected(self):
        with self.assertRaises(ValueError):
            self.parse('<trkseg/>')


if __name__ == "__main__":
    unittest.main()
