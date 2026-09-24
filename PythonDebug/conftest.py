"""Progress tracker for the exercise (infrastructure, not part of the puzzle: you can ignore it).

After every `python -m pytest` run this prints a compact summary and writes, inside `reports/`:

    status_board.txt   one row per test:  File | Test | Result | Change | Error     (open in Notepad)
    status_board.csv   the same table                                              (open in Excel)
    status_board.html  colour-coded page with PASS / FAIL filter buttons           (open in a browser)
    progress.txt       one block per run: totals, what you fixed, what newly broke
    history.csv        one line per run, handy for a chart of failures over time

The board REMEMBERS earlier runs. Run only one file (or use -k) and the other rows keep their last
known result; run the whole suite and the board is rebuilt from scratch.
Delete the `reports/` folder to start over.
"""
import csv
import html
import json
from datetime import datetime
from pathlib import Path

import pytest

_results = {}      # nodeid -> {"result": "PASS"|"FAIL"|"ERROR"|"SKIP", "error": str}
_session = None


def pytest_sessionstart(session):
    global _session
    _session = session
    _results.clear()


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    if call.excinfo is not None and report.failed:
        report._tracker_error = call.excinfo.exconly()


def pytest_runtest_logreport(report):
    if report.when == "call":
        result = "PASS" if report.passed else ("SKIP" if report.skipped else "FAIL")
    elif report.failed:
        result = "ERROR"          # a problem while setting up / tearing down the test
    else:
        return
    previous = _results.get(report.nodeid)
    if previous and previous["result"] in ("FAIL", "ERROR") and result == "PASS":
        return                    # keep the first problem
    _results[report.nodeid] = {"result": result, "error": _one_line(getattr(report, "_tracker_error", ""))}


def _one_line(message, limit=300):
    lines = [line for line in str(message).strip().splitlines() if line.strip()]
    if not lines:
        return ""
    text = " ".join(lines[0].split())
    return text if len(text) <= limit else text[:limit - 3] + "..."


def _split(nodeid):
    file, _, test = nodeid.partition("::")
    return file, test


def _shorten(text, width):
    return text if len(text) <= width else text[:width - 3] + "..."


def pytest_terminal_summary(terminalreporter, exitstatus, config):
    if not _results:
        return
    reports_dir = Path(config.rootpath) / "reports"
    reports_dir.mkdir(exist_ok=True)
    state_file = reports_dir / ".state.json"
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    try:
        old = json.loads(state_file.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        old = {}

    full_run = (len(_results) == _session.testscollected
                and not config.getoption("keyword") and not config.getoption("markexpr")
                and set(config.args) <= {"tests", "tests/", "tests\\"})
    board = {} if full_run else dict(old)
    for nodeid, data in _results.items():
        board[nodeid] = {"result": data["result"], "error": data["error"], "checked": now}

    fixed, broke, new_tests = [], [], []
    for nodeid, data in _results.items():
        before = old.get(nodeid, {}).get("result")
        bad = ("FAIL", "ERROR")
        if before is None:
            new_tests.append(nodeid)
            data["change"] = ""
        elif before in bad and data["result"] == "PASS":
            fixed.append(nodeid)
            data["change"] = "FIXED"
        elif before == "PASS" and data["result"] in bad:
            broke.append(nodeid)
            data["change"] = "NEW FAIL"
        else:
            data["change"] = ""
    for nodeid, entry in board.items():
        entry["change"] = _results.get(nodeid, {}).get("change", "")

    state_file.write_text(json.dumps(board, indent=1), encoding="utf-8")

    rows = []
    for nodeid, entry in board.items():
        file, test = _split(nodeid)
        rows.append({"file": file, "test": test, "result": entry["result"], "change": entry["change"],
                     "error": entry["error"], "checked": entry["checked"]})
    order = {"FAIL": 0, "ERROR": 0, "SKIP": 2, "PASS": 3}
    rows.sort(key=lambda row: (order.get(row["result"], 1), row["file"]))

    passed = sum(1 for r in rows if r["result"] == "PASS")
    failed = sum(1 for r in rows if r["result"] in ("FAIL", "ERROR"))
    total = len(rows)

    _write_txt(reports_dir / "status_board.txt", rows, now, passed, failed, total)
    _write_csv(reports_dir / "status_board.csv", rows)
    _write_html(reports_dir / "status_board.html", rows, now, passed, failed, total)
    _append_progress(reports_dir, now, full_run, passed, failed, total, fixed, broke)

    _print_summary(terminalreporter, rows, passed, failed, total, fixed, broke, reports_dir, full_run)


def _by_file(rows):
    files = {}
    for row in rows:
        entry = files.setdefault(row["file"], [0, 0])
        entry[0 if row["result"] == "PASS" else 1] += 1
    return files


def _table(headers, data):
    widths = [max(len(str(x)) for x in [h] + [row[i] for row in data]) for i, h in enumerate(headers)]
    line = "  ".join(h.ljust(w) for h, w in zip(headers, widths))
    out = [line, "  ".join("-" * w for w in widths)]
    for row in data:
        out.append("  ".join(str(cell).ljust(w) for cell, w in zip(row, widths)))
    return "\n".join(out)


def _write_txt(path, rows, now, passed, failed, total):
    lines = [f"STATUS BOARD   updated {now}", f"PASS {passed}   FAIL {failed}   total {total}", ""]
    per_file = _by_file(rows)
    lines.append("By file:")
    lines.append(_table(["File", "PASS", "FAIL"], [[f, v[0], v[1]] for f, v in sorted(per_file.items())]))
    lines.append("")
    lines.append("Every test (failures first):")
    lines.append(_table(["File", "Test", "Result", "Change", "Error"],
                        [[r["file"], _shorten(r["test"], 60), r["result"], r["change"],
                          _shorten(r["error"], 110)] for r in rows]))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _write_csv(path, rows):
    with path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle)
        writer.writerow(["File", "Test", "Result", "Change", "Error", "Last checked"])
        for r in rows:
            writer.writerow([r["file"], r["test"], r["result"], r["change"], r["error"], r["checked"]])


def _write_html(path, rows, now, passed, failed, total):
    per_file = _by_file(rows)
    file_rows = "".join(
        f"<tr><td>{html.escape(f)}</td><td class='p'>{v[0]}</td><td class='f'>{v[1]}</td></tr>"
        for f, v in sorted(per_file.items()))
    body_rows = []
    for r in rows:
        css = "pass" if r["result"] == "PASS" else ("skip" if r["result"] == "SKIP" else "fail")
        change = f"<b>{html.escape(r['change'])}</b>" if r["change"] else ""
        body_rows.append(
            f"<tr class='{css}'><td>{html.escape(r['file'])}</td><td>{html.escape(r['test'])}</td>"
            f"<td class='res'>{html.escape(r['result'])}</td><td>{change}</td>"
            f"<td class='err'>{html.escape(r['error'])}</td></tr>")
    page = f"""<!doctype html><html><head><meta charset="utf-8"><title>Status board</title><style>
body{{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#1f2933}}
h1{{margin:0 0 4px}} .sub{{color:#52606d;margin-bottom:16px}}
.big span{{display:inline-block;margin-right:22px;font-size:20px;font-weight:600}}
.p{{color:#0a7d33}} .f{{color:#c0281c}}
table{{border-collapse:collapse;margin:12px 0;font-size:13px}}
th{{background:#0b3d63;color:#fff;text-align:left;padding:6px 10px}}
td{{border:1px solid #d5dde5;padding:5px 10px;vertical-align:top}}
tr.fail td.res{{background:#fde8e6;color:#c0281c;font-weight:700}}
tr.pass td.res{{background:#e3f6e8;color:#0a7d33;font-weight:700}}
tr.skip td.res{{background:#fff4d6;color:#8a6100;font-weight:700}}
td.err{{font-family:Consolas,monospace;font-size:12px;color:#7a1d15}}
button{{padding:5px 12px;margin-right:6px;border:1px solid #9aa5b1;background:#fff;border-radius:4px;cursor:pointer}}
button.on{{background:#0b3d63;color:#fff}}
</style></head><body>
<h1>Status board</h1><div class="sub">updated {html.escape(now)}</div>
<div class="big"><span class="p">PASS {passed}</span><span class="f">FAIL {failed}</span><span>total {total}</span></div>
<table><tr><th>File</th><th>PASS</th><th>FAIL</th></tr>{file_rows}</table>
<div><button class="on" onclick="show('all',this)">All</button>
<button onclick="show('fail',this)">FAIL only</button><button onclick="show('pass',this)">PASS only</button></div>
<table id="board"><tr><th>File</th><th>Test</th><th>Result</th><th>Change</th><th>Error</th></tr>
{''.join(body_rows)}</table>
<script>
function show(kind,btn){{
  document.querySelectorAll('button').forEach(b=>b.classList.remove('on')); btn.classList.add('on');
  document.querySelectorAll('#board tr[class]').forEach(tr=>{{
    tr.style.display = (kind==='all' || tr.classList.contains(kind)) ? '' : 'none';
  }});
}}
</script></body></html>"""
    path.write_text(page, encoding="utf-8")


def _append_progress(reports_dir, now, full_run, passed, failed, total, fixed, broke):
    scope = "full run" if full_run else "partial run"
    lines = [f"[{now}] PASS {passed} / FAIL {failed} / total {total}   "
             f"(+{len(fixed)} fixed, {len(broke)} newly failing)   {scope}"]
    if fixed:
        lines.append("    Fixed this run:")
        lines += [f"      - {nodeid}" for nodeid in fixed]
    if broke:
        lines.append("    Newly failing:")
        lines += [f"      - {nodeid}" for nodeid in broke]
    with (reports_dir / "progress.txt").open("a", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    history = reports_dir / "history.csv"
    is_new = not history.exists()
    with history.open("a", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        if is_new:
            writer.writerow(["time", "scope", "total", "passed", "failed", "fixed", "newly_failing"])
        writer.writerow([now, scope, total, passed, failed, len(fixed), len(broke)])


def _print_summary(tr, rows, passed, failed, total, fixed, broke, reports_dir, full_run):
    tr.write_sep("=", "TEST TRACKER")
    per_file = _by_file(rows)
    tr.write_line(_table(["File", "PASS", "FAIL", "Status"],
                         [[f, v[0], v[1], "PASS" if v[1] == 0 else "FAIL"] for f, v in sorted(per_file.items())]))
    tr.write_line("")
    failing = [r for r in rows if r["result"] in ("FAIL", "ERROR")]
    if failing:
        shown = failing[:20]
        tr.write_line(_table(["File", "Test", "Result", "Error"],
                             [[r["file"].replace("tests/", ""), _shorten(r["test"], 48), r["result"],
                               _shorten(r["error"], 70)] for r in shown]))
        if len(failing) > len(shown):
            tr.write_line(f"... and {len(failing) - len(shown)} more failing (see the full board below)")
        tr.write_line("")
    tr.write_line(f"PASS {passed}   FAIL {failed}   total {total}   "
                  f"({'full' if full_run else 'partial'} run)   "
                  f"+{len(fixed)} fixed, {len(broke)} newly failing since the last run")
    tr.write_line(f"Full board: {reports_dir / 'status_board.txt'}   |   "
                  f"{reports_dir / 'status_board.html'}   |   {reports_dir / 'status_board.csv'}")
