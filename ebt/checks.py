"""Чекеры bodega для ebt_check (--checkers).

Общие типы — в qa/ebt-kit/tool/ebt_check/checkers.py. Здесь — только правила этого приложения
из spec.toml рядом. Чекер реализует текст требования, не импортирует код приложения и не
смотрит на ok.

Допущение о данных: тест узнаёт путь в recheck_fails по номеру перепроверки — фейковое
хранилище отдаёт файлы в порядке вставки, а в сценариях со сбоем перепроверки все файлы —
старые сироты. Соответствие номера перепроверки пути проверено для текущей реализации уборки.
При изменении порядка обхода этот сценарий требует пересмотра; корректность свидетельств
автоматически не гарантируется.
"""

from __future__ import annotations

from typing import Any

from checkers import Verdict, WitnessError

WEEK_MS = 7 * 24 * 60 * 60 * 1000

# ── Общие проверки формы ──────────────────────────────────────────────


def _int(v: Any, name: str) -> int:
    # bool — подкласс int в Python, но в свидетельстве это ошибка типа.
    if isinstance(v, bool) or not isinstance(v, int):
        raise WitnessError(f"{name}: ожидалось целое, получено {v!r}")
    return v


def _flag(v: Any, name: str) -> bool:
    if not isinstance(v, bool):
        raise WitnessError(f"{name}: ожидалось true/false, получено {v!r}")
    return v


def _paths(v: Any, name: str) -> list[str]:
    if not isinstance(v, list) or not all(isinstance(p, str) for p in v):
        raise WitnessError(f"{name}: ожидался список путей-строк, получено {v!r}")
    return v


def _files(v: Any) -> list[tuple[str, int]]:
    if not isinstance(v, list):
        raise WitnessError(f"files: ожидался список, получено {type(v).__name__}")
    out = []
    for i, f in enumerate(v):
        if not isinstance(f, list) or len(f) != 2 or not isinstance(f[0], str):
            raise WitnessError(f"files[{i}]: ожидалось [path, modified_at], получено {f!r}")
        out.append((f[0], _int(f[1], f"files[{i}].modified_at")))
    return out


# ── REQ-PHOTO-SWEEP ───────────────────────────────────────────────────


def orphan_sweep(w: dict, inv: dict) -> Verdict:
    now = _int(w["now"], "now")
    files = _files(w["files"])
    used_first = set(_paths(w["used_first"], "used_first"))
    used_recheck = set(_paths(w["used_recheck"], "used_recheck"))
    leased = set(_paths(w["leased"], "leased"))
    during = set(_paths(w["deleted_during_sweep"], "deleted_during_sweep"))
    first_fails = _flag(w["first_read_fails"], "first_read_fails")
    recheck_fails = set(_paths(w["recheck_fails"], "recheck_fails"))
    delete_fails = set(_paths(w["delete_fails"], "delete_fails"))
    remaining = _paths(w["remaining"], "remaining")
    removed = _int(w["removed"], "removed")

    protected = used_first | used_recheck | leased | during | recheck_fails | delete_fails
    old = {p for p, modified in files if now - modified > WEEK_MS}
    deletable = set() if first_fails else old - protected
    expected = sorted(p for p, _ in files if p not in deletable)

    # Класс входов засчитывается, только если запись его действительно проверяет: причина,
    # по которой файл должен остаться, — единственная (или первая по ходу уборки), иначе
    # неверная реализация прошла бы этот класс за счёт другой защиты.
    parts: set[str] = set()
    if not files:
        parts.add("empty")
    if first_fails and files:
        parts.add("first_read_fails")
    failed_seen = False
    for p, modified in files:
        if first_fails:
            continue
        age = now - modified
        if p not in protected and age < WEEK_MS:
            parts.add("young_orphan_kept")
        elif p not in protected and age == WEEK_MS:
            parts.add("exact_week_kept")
        if p not in old:
            continue
        if p in deletable and p not in remaining:
            parts.add("old_orphan_deleted")
            if failed_seen:
                parts.add("continues_after_failure")
        if p in used_first:
            parts.add("used_kept")
            continue
        if p in used_recheck:
            parts.add("undo_between_passes")
            continue
        if p in leased:
            parts.add("leased_kept")
            continue
        if p in during:
            parts.add("deleted_during_sweep_kept")
            continue
        if p in recheck_fails:
            parts.add("recheck_fails")
            failed_seen = True
        elif p in delete_fails:
            parts.add("delete_fails")
            failed_seen = True

    lost = sorted(set(expected) - set(remaining))
    extra = sorted(set(remaining) - set(expected))
    problems = []
    if lost:
        problems.append(f"удалены, хотя должны остаться: {', '.join(lost)}")
    if extra:
        problems.append(f"остались, хотя должны быть удалены или их не было: {', '.join(extra)}")
    if removed != len(deletable):
        problems.append(f"removed={removed}, а удалить по правилу нужно {len(deletable)}")
    return Verdict(not problems, "; ".join(problems), parts)


CHECKERS: dict = {
    "orphan_sweep": orphan_sweep,
}
