import unittest
from datetime import date

from src import utils


class ClassPingTests(unittest.TestCase):
    def test_prefilter_test(self):
        self.assertTrue(
            utils.likely_academic_event(
                "Revision Test of SST Chapter 1 on Monday"
            )
        )

    def test_prefilter_notebook(self):
        self.assertTrue(
            utils.likely_academic_event(
                "Bring your Science notebook for checking"
            )
        )

    def test_prefilter_irrelevant(self):
        self.assertFalse(
            utils.likely_academic_event(
                "School transport route timing updated"
            )
        )

    def test_clean_student_text(self):
        cleaned = utils.clean_student_text(
            "Dear Students, Revision Test Chapter 1. "
            "Regards, Class Teacher"
        )
        self.assertNotIn("Dear Students", cleaned)
        self.assertNotIn("Regards", cleaned)

    def test_source_id_is_stable(self):
        first = utils.source_id(
            "Test tomorrow",
            date(2026, 9, 25),
        )
        second = utils.source_id(
            "Test tomorrow",
            date(2026, 9, 25),
        )
        self.assertEqual(first, second)

    def test_json_fence(self):
        fence = chr(96) * 3
        value = utils.extract_json_object(
            fence + 'json\n{"results": []}\n' + fence
        )
        self.assertEqual(value, {"results": []})

    def test_unknown_type_becomes_other(self):
        event = utils.normalize_event(
            {"type": "banana", "title": "X"}
        )
        self.assertEqual(event["type"], "other")
        self.assertTrue(event["needsReview"])


if __name__ == "__main__":
    unittest.main()
