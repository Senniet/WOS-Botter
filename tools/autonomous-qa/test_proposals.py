from pathlib import Path
import tempfile
import unittest
import subprocess
import proposals

class ProposalTest(unittest.TestCase):
    def test_only_stable_screen_creates_inactive_template(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);p=root/'screen.png'
            subprocess.run(['magick','-size','720x1280','xc:blue',
                 '-fill','white','-draw','rectangle 120,225 430,260',str(p)],check=True)
            result=proposals.propose_unknown(root/'pending',p,p)
            self.assertIsNotNone(result)
            self.assertEqual(result['status'],'pending_manual_review')
            self.assertTrue((root/'pending/candidate.png').is_file())
            self.assertIn('one negative',result['requirements'][1])

if __name__=='__main__':unittest.main()
