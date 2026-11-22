import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("memory_sampler", Path(__file__).parents[1] / "sample-process-memory.py")
memory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(memory)


class ProcessMemoryTest(unittest.TestCase):
    def test_api30_pss_summary(self):
        parsed = memory.parse_meminfo("""App Summary
              Java Heap: 17000
            Native Heap: 22000
               Graphics: 33000
                  TOTAL: 99000       TOTAL SWAP PSS: 0
        """)
        self.assertEqual({"javaPssKb": 17000, "nativePssKb": 22000, "graphicsPssKb": 33000,
                          "totalPssKb": 99000}, parsed)

    def test_api36_pss_rss_summary_does_not_confuse_rss_with_pss(self):
        parsed = memory.parse_meminfo("""Native Heap       99999 8888
            App Summary
                           Pss(KB)                        Rss(KB)
                 Java Heap: 10                               2000
               Native Heap: 20                               3000
                  Graphics: 0                                   0
                 TOTAL PSS: 40                  TOTAL RSS: 5000
        """)
        self.assertEqual(20, parsed["nativePssKb"])
        self.assertEqual(40, parsed["totalPssKb"])

    def test_killed_process_is_unavailable_not_a_zero_memory_success(self):
        self.assertIsNone(memory.parse_meminfo("No process found for: app.zorik.player"))

    def test_incomplete_or_zero_evidence_is_rejected(self):
        for output in ["", "App Summary\nTOTAL PSS: 4", "App Summary\nJava Heap: 1\nNative Heap: 2\nGraphics: 3\nTOTAL: 0"]:
            with self.assertRaises(ValueError):
                memory.parse_meminfo(output)
        with self.assertRaises(ValueError):
            memory.summarize([])

    def test_peak_is_computed_per_memory_category(self):
        self.assertEqual({"totalPssKb": 20, "nativePssKb": 8}, memory.summarize([
            {"totalPssKb": 10, "nativePssKb": 8}, {"totalPssKb": 20, "nativePssKb": 3},
        ]))
