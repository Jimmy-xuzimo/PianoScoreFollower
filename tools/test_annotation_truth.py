import tempfile
import unittest
from pathlib import Path
from annotation_truth import annotation_anchors


class AnnotationTruthTest(unittest.TestCase):
    def test_unfolded_match_measure_uses_explicit_note_repeat_visit(self):
        xml = '<score-partwise><part><measure number="0"><attributes><divisions>2</divisions></attributes><note id="n1"><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration></note></measure><measure number="1"><note id="n2"><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure></part></score-partwise>'
        graph = [['0','480','0','0','0'], ['480','1920','1','0','480'], ['2400','480','0','1','0'], ['2880','1920','1','1','480']]
        match = 'snote(n1-2,[G,n],4,57:1,0,1/8,111.5,112,[v1])-note(n560,67,1000,1100,60,1,0).'
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'score.xml'
            path.write_text(xml)
            anchors = annotation_anchors(path, graph, match, 500000, 480)
        self.assertEqual(anchors[0][1], 2.5)


if __name__ == '__main__':
    unittest.main()
