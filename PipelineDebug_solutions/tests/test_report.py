import pytest

from pipeline.report import (format_money, format_percent, render_monthly_report, render_summary,
                             rows_to_csv, write_rejects)


@pytest.mark.parametrize("amount,expected", [
    (0, "$0.00"), (5, "$5.00"), (1234.5, "$1,234.50"), (1234567.891, "$1,234,567.89"),
    (0.5, "$0.50"), (-5, "-$5.00"), (-1234.5, "-$1,234.50"),
])
def test_format_money(amount, expected):
    assert format_money(amount) == expected


@pytest.mark.parametrize("value,expected", [
    (5.0, "+5.0%"), (-2.5, "-2.5%"), (0.0, "+0.0%"), (100.0, "+100.0%"), (None, "n/a"),
])
def test_format_percent(value, expected):
    assert format_percent(value) == expected


def test_rows_to_csv_header_and_order():
    rows = [{"a": 1, "b": "x"}, {"a": 2, "b": "y"}]
    assert rows_to_csv(rows, ["b", "a"]) == "b,a\nx,1\ny,2\n"


def test_rows_to_csv_quotes_special_characters():
    text = rows_to_csv([{"a": "one, two", "b": 'say "hi"'}], ["a", "b"])
    assert text == 'a,b\n"one, two","say ""hi"""\n'


def test_rows_to_csv_no_rows_is_header_only():
    assert rows_to_csv([], ["a", "b"]) == "a,b\n"


def test_render_monthly_report():
    monthly = {"2024-12": 1234.5, "2024-11": 1000.0}
    growth = {"2024-11": None, "2024-12": 23.5}
    assert render_monthly_report(monthly, growth) == (
        "2024-11  $1,000.00  n/a\n"
        "2024-12  $1,234.50  +23.5%")


def test_render_summary_fixed_order():
    summary = {"watermark": "2025-02-27", "updated": 0, "inserted": 5, "loaded": 5, "orphans": 1,
               "duplicates_removed": 2, "rejected": 3, "read": 9}
    assert render_summary(summary).splitlines() == [
        "read: 9", "rejected: 3", "duplicates_removed: 2", "orphans: 1", "loaded: 5",
        "inserted: 5", "updated: 0", "watermark: 2025-02-27"]


def test_render_summary_no_watermark():
    summary = {k: 0 for k in ("read", "rejected", "duplicates_removed", "orphans", "loaded",
                              "inserted", "updated")}
    summary["watermark"] = None
    assert render_summary(summary).splitlines()[-1] == "watermark: none"


def test_write_rejects(tmp_path):
    path = tmp_path / "rejects.csv"
    write_rejects(path, [{"line": 3, "reason": "bad, very bad"}, {"line": 9, "reason": "worse"}])
    assert path.read_text(encoding="utf-8") == 'line,reason\n3,"bad, very bad"\n9,worse\n'
