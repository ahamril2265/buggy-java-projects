"""Logic behind the tracker GUI (no tkinter in here, so it can be tested and reused).

  run_tests()        run pytest and return one result dict per test
  diff_results()     compare two runs -> which tests were FIXED / are NEW FAILURES
  BugLog             the personal bug journal, saved as JSON
  RunHistory         one line per test run, saved as JSON
"""
import json
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

PROJECT_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_DIR / "tracker_data"

# The pipeline stages, in data-flow order: (label, test file, source file)
STAGES = [
    ("Ingest", "test_ingest.py", "ingest.py"),
    ("Clean", "test_clean.py", "clean.py"),
    ("Transform", "test_transform.py", "transform.py"),
    ("Aggregate", "test_aggregate.py", "aggregate.py"),
    ("Store", "test_store.py", "store.py"),
    ("Report", "test_report.py", "report.py"),
]
E2E_FILE = "test_pipeline_e2e.py"

MODULES = ["ingest", "clean", "transform", "aggregate", "store", "report", "runner", "config",
           "data files", "unknown"]
CATEGORIES = [
    "Logic / off-by-one", "Wrong operator / condition", "Ordering / sorting / tie-break",
    "Rounding / numbers", "Dates / time", "Data cleaning / normalisation", "Join / lookup",
    "Aggregation", "SQL", "Idempotency / incremental load", "State / mutation", "Error handling",
    "Formatting / output", "Other",
]
BUG_STATUSES = ["Suspected", "Investigating", "Found", "Fixed", "Won't fix"]


# ---------------------------------------------------------------------- running pytest
def _one_line(text, limit=180):
    line = " ".join((text or "").split("\n")[0].split())
    return line if len(line) <= limit else line[: limit - 1] + "…"


def _failure_location(text):
    """The deepest 'file.py:LINE: in function' frame of a --tb=short traceback."""
    frames = re.findall(r"^(\S+\.py):(\d+): in (\S+)", text or "", flags=re.MULTILINE)
    if not frames:
        return ""
    path, line, func = frames[-1]
    return f"{Path(path).name}:{line} {func}"


def parse_junit(xml_path):
    results = []
    for case in ET.parse(xml_path).getroot().iter("testcase"):
        module = case.get("classname", "").split(".")
        file_part = next((p for p in reversed(module) if p.startswith("test_")), module[-1])
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        skipped = case.find("skipped")
        if problem is not None:
            status, message, detail = "FAIL", problem.get("message", ""), problem.text or ""
        elif skipped is not None:
            status, message, detail = "SKIP", skipped.get("message", ""), ""
        else:
            status, message, detail = "PASS", "", ""
        results.append({
            "file": file_part + ".py",
            "test": case.get("name", ""),
            "status": status,
            "error": _one_line(message),
            "where": _failure_location(detail),
            "detail": (message + "\n\n" + detail).strip() if status == "FAIL" else "",
            "seconds": float(case.get("time", 0) or 0),
        })
    return results


def run_tests(targets=None, keyword=None, timeout=300):
    """Run pytest in the project folder. `targets` is a list of paths / node ids."""
    with tempfile.TemporaryDirectory() as tmp:
        xml_path = Path(tmp) / "result.xml"
        command = [sys.executable, "-m", "pytest", f"--junitxml={xml_path}", "-p", "no:cacheprovider",
                   "-q", "--tb=short"]
        if keyword:
            command += ["-k", keyword]
        command += list(targets or [])
        completed = subprocess.run(command, cwd=PROJECT_DIR, capture_output=True, text=True,
                                   timeout=timeout)
        if not xml_path.exists():
            raise RuntimeError("pytest produced no result file:\n" + completed.stdout + completed.stderr)
        return parse_junit(xml_path)


def result_key(result):
    return f"{result['file']}::{result['test']}"


def diff_results(previous, current):
    """previous / current: {key: status}. Returns (fixed_keys, newly_failing_keys)."""
    fixed = sorted(k for k, s in current.items() if s == "PASS" and previous.get(k) == "FAIL")
    broken = sorted(k for k, s in current.items() if s == "FAIL" and previous.get(k) == "PASS")
    return fixed, broken


def summarise(results):
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")
    total = len(results)
    return {"passed": passed, "failed": failed, "total": total,
            "percent": round(100 * passed / total, 1) if total else 0.0}


def stage_health(results):
    """{test_file: (passed, total)} for the pipeline map."""
    health = {}
    for r in results:
        passed, total = health.get(r["file"], (0, 0))
        health[r["file"]] = (passed + (r["status"] == "PASS"), total + 1)
    return health


# ---------------------------------------------------------------------- JSON persistence
def _load_json(path, default):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def _save_json(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, indent=2), encoding="utf-8")


def now_text():
    return datetime.now().strftime("%Y-%m-%d %H:%M")


class RunHistory:
    def __init__(self, path=None):
        self.path = Path(path or DATA_DIR / "history.json")
        self.runs = _load_json(self.path, [])

    def add(self, summary, fixed, broken):
        self.runs.append({"time": now_text(), **summary, "fixed": len(fixed), "broken": len(broken)})
        _save_json(self.path, self.runs)


class LastRun:
    """The status of every test at the previous run, used to spot FIXED / NEW FAIL."""

    def __init__(self, path=None):
        self.path = Path(path or DATA_DIR / "last_run.json")

    def load(self):
        return _load_json(self.path, {})

    def save(self, statuses):
        _save_json(self.path, statuses)


class BugLog:
    FIELDS = ("title", "module", "category", "status", "linked_test", "hypothesis", "root_cause",
              "fix", "learned")

    def __init__(self, path=None):
        self.path = Path(path or DATA_DIR / "bug_log.json")
        self.entries = _load_json(self.path, [])

    def _save(self):
        _save_json(self.path, self.entries)

    def add(self, **fields):
        next_id = max((e["id"] for e in self.entries), default=0) + 1
        entry = {"id": next_id, "created": now_text(), "updated": now_text()}
        for name in self.FIELDS:
            entry[name] = fields.get(name, "")
        entry["status"] = entry["status"] or "Suspected"
        self.entries.append(entry)
        self._save()
        return entry

    def get(self, entry_id):
        return next((e for e in self.entries if e["id"] == entry_id), None)

    def update(self, entry_id, **fields):
        entry = self.get(entry_id)
        if entry is None:
            raise KeyError(entry_id)
        for name in self.FIELDS:
            if name in fields:
                entry[name] = fields[name]
        entry["updated"] = now_text()
        self._save()
        return entry

    def delete(self, entry_id):
        self.entries = [e for e in self.entries if e["id"] != entry_id]
        self._save()

    def counts(self):
        counts = {status: 0 for status in BUG_STATUSES}
        for entry in self.entries:
            counts[entry["status"]] = counts.get(entry["status"], 0) + 1
        return counts


GUIDE_TEXT = """\
WHAT THIS PROJECT IS
A small sales-data pipeline. Raw CSV files come in, get validated, cleaned, joined, aggregated, stored in SQLite and turned into a report. It has bugs. There is no list of them - finding them is the exercise.

THE DATA FLOW
  orders.csv / customers.csv / products.csv
     -> 1 INGEST     read files, parse dates, validate rows, drop duplicates
     -> 2 CLEAN      normalise emails / countries / statuses, convert currency
     -> 3 TRANSFORM  join orders with customers + products, apply coupons, compute revenue/profit
     -> 4 AGGREGATE  totals, top customers, moving averages, percentiles, growth, retention
     -> 5 STORE      upsert into SQLite, watermark for incremental loads, SQL queries
     -> 6 REPORT     format money / percent, CSV text, run summary
  runner.py runs steps 1-5 (+ the reject log) in order.
  Every source file starts with a docstring - that docstring is the specification.

GROUND RULES
  * Fix the code in pipeline/. Never edit the tests, the data files or config.py.
  * A test failing tells you WHAT is wrong, not WHERE the bug is. The "Where" column shows where the error was RAISED, which is often not where the bug lives.
  * The end-to-end tests fail when ANY stage upstream is wrong. Fix the unit-level failures first and watch the end-to-end ones turn green by themselves.
  * A green test does not prove the code is right. Probe your fix with inputs the tests do not use.

A DEBUGGING METHOD THAT WORKS
  1. Pick ONE failing test. Prefer the smallest unit test in the earliest stage.
  2. Read the docstring (the spec) and the test. Say in one sentence what should happen.
  3. Run the function by hand with the test's input and print what you actually get.
  4. Form a hypothesis, write it in the Bug Log, then change ONE thing.
  5. Re-run. If more tests turn green than you expected, note it - bugs are often shared.
  6. Write down what caused it and what you learned (Bug Log -> Root cause / What I learned).

WHERE BUGS LIKE TO HIDE  (checklist, no hints about which apply here)
  - Off-by-one and comparison operators (< vs <=, > vs >=)
  - Sorting and tie-breaking; first vs last; ascending vs descending
  - Rounding: float vs Decimal, half-up vs banker's rounding
  - Dates: day/month order, month/year boundaries
  - Order of operations: cleaning before or after a comparison / join
  - Mutable state and shared references
  - Wrong denominator / wrong base in a percentage
  - Idempotency: does running the same load twice change the result?
  - Incremental loads: boundaries, late data, moving a marker backwards
  - SQL: WHERE vs HAVING, GROUP BY, NULLs, ORDER BY, LIMIT
  - Formatting: signs, separators, decimals
"""
