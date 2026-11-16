"""Тесты чекера bodega (checks.py) по spec.toml рядом.

PYTHONPATH=<qa>/qa/ebt-kit/tool/ebt_check python3 -m unittest discover -s ebt
"""

from __future__ import annotations

import tomllib
import unittest
from pathlib import Path

from checkers import WitnessError

import checks

SPEC = tomllib.loads((Path(__file__).parent / "spec.toml").read_text(encoding="utf-8"))
INV = SPEC["inv"]["INV-SWEEP-OBS"]

DAY = 24 * 60 * 60 * 1000
WEEK = 7 * DAY
NOW = 100 * DAY
OLD = NOW - 30 * DAY


def w(files, remaining, removed, **kw):
    """Свидетельство уборки: files — [(путь, modified_at)], остальное по умолчанию пусто."""
    base = dict(now=NOW, files=[list(f) for f in files], used_first=[], used_recheck=[], leased=[],
                deleted_during_sweep=[], first_read_fails=False, recheck_fails=[], delete_fails=[],
                remaining=remaining, removed=removed)
    base.update(kw)
    return base


def check(witness):
    return checks.orphan_sweep(witness, INV)


class SpecShape(unittest.TestCase):
    def test_checker_registered_and_witness_matches(self):
        self.assertIn(INV["checker"], checks.CHECKERS)
        self.assertEqual(set(w([], [], 0)), set(INV["witness"]))


class Proves(unittest.TestCase):
    """Корректная уборка: ok и свой класс входов."""

    def assertProves(self, witness, part):
        v = check(witness)
        self.assertTrue(v.ok, v.reason)
        self.assertIn(part, v.parts)
        self.assertLessEqual(v.parts, set(INV["partitions"]))

    def test_empty(self):
        self.assertProves(w([], [], 0), "empty")

    def test_old_orphan_deleted(self):
        self.assertProves(w([("a", OLD)], [], 1), "old_orphan_deleted")

    def test_young_orphan_kept(self):
        self.assertProves(w([("a", NOW - DAY)], ["a"], 0), "young_orphan_kept")

    def test_exact_week_kept(self):
        self.assertProves(w([("a", NOW - WEEK)], ["a"], 0), "exact_week_kept")

    def test_used_kept(self):
        self.assertProves(w([("a", OLD)], ["a"], 0, used_first=["a"], used_recheck=["a"]), "used_kept")

    def test_leased_kept(self):
        self.assertProves(w([("a", OLD)], ["a"], 0, leased=["a"]), "leased_kept")

    def test_undo_between_passes(self):
        self.assertProves(w([("a", OLD)], ["a"], 0, used_recheck=["a"]), "undo_between_passes")

    def test_deleted_during_sweep_kept(self):
        self.assertProves(w([("a", OLD)], ["a"], 0, deleted_during_sweep=["a"]), "deleted_during_sweep_kept")

    def test_first_read_fails(self):
        self.assertProves(w([("a", OLD)], ["a"], 0, first_read_fails=True), "first_read_fails")

    def test_recheck_fails_keeps_that_file_and_continues(self):
        witness = w([("a", OLD), ("b", OLD)], ["a"], 1, recheck_fails=["a"])
        self.assertProves(witness, "recheck_fails")
        self.assertProves(witness, "continues_after_failure")

    def test_delete_fails_keeps_that_file_and_continues(self):
        witness = w([("a", OLD), ("b", OLD)], ["a"], 1, delete_fails=["a"])
        self.assertProves(witness, "delete_fails")
        self.assertProves(witness, "continues_after_failure")

    def test_failure_after_last_deleted_is_not_continuation(self):
        v = check(w([("a", OLD), ("b", OLD)], ["b"], 1, delete_fails=["b"]))
        self.assertTrue(v.ok, v.reason)
        self.assertNotIn("continues_after_failure", v.parts)


class Catches(unittest.TestCase):
    """Нарушения требования: чекер говорит «нет» и называет путь."""

    def assertCatches(self, witness, path=None):
        v = check(witness)
        self.assertFalse(v.ok)
        if path:
            self.assertIn(path, v.reason)

    def test_deleted_used(self):
        self.assertCatches(w([("a", OLD)], [], 1, used_first=["a"], used_recheck=["a"]), "a")

    def test_deleted_leased(self):
        self.assertCatches(w([("a", OLD)], [], 1, leased=["a"]), "a")

    def test_deleted_young(self):
        self.assertCatches(w([("a", NOW - DAY)], [], 1), "a")

    def test_deleted_exact_week(self):
        self.assertCatches(w([("a", NOW - WEEK)], [], 1), "a")

    def test_deleted_when_first_read_fails(self):
        self.assertCatches(w([("a", OLD)], [], 1, first_read_fails=True), "a")

    def test_deleted_after_undo(self):
        self.assertCatches(w([("a", OLD)], [], 1, used_recheck=["a"]), "a")

    def test_deleted_during_sweep(self):
        self.assertCatches(w([("a", OLD)], [], 1, deleted_during_sweep=["a"]), "a")

    def test_old_orphan_not_deleted(self):
        self.assertCatches(w([("a", OLD)], ["a"], 0), "a")

    def test_removed_count_differs(self):
        self.assertCatches(w([("a", OLD)], [], 0))

    def test_unknown_path_in_remaining(self):
        self.assertCatches(w([], ["ghost"], 0), "ghost")

    def test_recheck_failure_stopped_the_sweep(self):
        self.assertCatches(w([("a", OLD), ("b", OLD)], ["a", "b"], 0, recheck_fails=["a"]), "b")

    def test_recheck_failure_kept_the_wrong_file(self):
        self.assertCatches(w([("a", OLD), ("b", OLD)], ["b"], 1, recheck_fails=["a"]), "a")

    def test_delete_failure_stopped_the_sweep(self):
        self.assertCatches(w([("a", OLD), ("b", OLD)], ["a", "b"], 0, delete_fails=["a"]), "b")

    def test_delete_failure_kept_the_wrong_file(self):
        self.assertCatches(w([("a", OLD), ("b", OLD)], ["b"], 1, delete_fails=["a"]), "a")


class Shape(unittest.TestCase):
    def test_fractional_now(self):
        with self.assertRaises(WitnessError):
            check(w([], [], 0, now=1.5))

    def test_file_not_a_pair(self):
        with self.assertRaises(WitnessError):
            check(w([("a", OLD, 1)], [], 0))

    def test_flag_not_bool(self):
        with self.assertRaises(WitnessError):
            check(w([], [], 0, first_read_fails=0))

    def test_paths_not_strings(self):
        with self.assertRaises(WitnessError):
            check(w([], [], 0, leased=[1]))


class HonestPartitions(unittest.TestCase):
    """Класс входов засчитывается, только если запись его действительно проверяет."""

    def parts(self, witness):
        v = check(witness)
        self.assertTrue(v.ok, v.reason)
        return v.parts

    def test_age_class_needs_age_to_be_the_only_reason(self):
        self.assertNotIn("exact_week_kept", self.parts(w([("a", NOW - WEEK)], ["a"], 0, leased=["a"])))
        self.assertNotIn("exact_week_kept", self.parts(w([("a", NOW - WEEK)], ["a"], 0, first_read_fails=True)))
        self.assertNotIn("young_orphan_kept", self.parts(w([("a", NOW - DAY)], ["a"], 0, used_recheck=["a"])))
        self.assertNotIn("young_orphan_kept", self.parts(w([("a", NOW - DAY)], ["a"], 0, delete_fails=["a"])))

    def test_deleted_during_sweep_needs_a_race(self):
        self.assertNotIn("deleted_during_sweep_kept", self.parts(
            w([("a", OLD)], ["a"], 0, used_first=["a"], deleted_during_sweep=["a"])))
        self.assertNotIn("deleted_during_sweep_kept", self.parts(
            w([("a", OLD)], ["a"], 0, leased=["a"], deleted_during_sweep=["a"])))

    def test_failure_classes_need_an_otherwise_deletable_file(self):
        parts = self.parts(w([("a", OLD), ("b", OLD)], ["a"], 1, leased=["a"], delete_fails=["a"]))
        self.assertNotIn("delete_fails", parts)
        self.assertNotIn("continues_after_failure", parts)
        parts = self.parts(w([("a", OLD), ("b", OLD)], ["a"], 1, used_recheck=["a"], recheck_fails=["a"]))
        self.assertNotIn("recheck_fails", parts)
        self.assertNotIn("continues_after_failure", parts)
        self.assertNotIn("delete_fails", self.parts(w([("a", OLD)], ["a"], 0, first_read_fails=True, delete_fails=["a"])))

    def test_protection_classes_need_their_own_reason(self):
        self.assertNotIn("leased_kept", self.parts(w([("a", OLD)], ["a"], 0, first_read_fails=True, leased=["a"])))
        self.assertNotIn("undo_between_passes", self.parts(
            w([("a", OLD)], ["a"], 0, first_read_fails=True, used_recheck=["a"])))


if __name__ == "__main__":
    unittest.main()
