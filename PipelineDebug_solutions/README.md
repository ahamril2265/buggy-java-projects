# PipelineDebug - a sales data pipeline with bugs in it

A small but realistic **ETL (extract - transform - load) pipeline** written in plain Python. It reads three messy CSV files, cleans and joins them, computes business numbers, stores them in SQLite and prints a report.

**It contains bugs. There is no list of them, and no hints.** Your job is to make every test pass *and* make the pipeline actually correct. This is harder than the previous Python round: the bugs are spread across several stages, some hide behind others, and a green test does not always mean correct code.

## Setup

```bash
pip install -r requirements.txt
python tracker_gui.py          # opens the Debug Tracker window (see below)
python -m pytest               # or run the tests from the terminal
```

Python 3.10+. Only `pytest` is needed. The GUI uses `tkinter`, which ships with Python on Windows and macOS (on Debian/Ubuntu: `sudo apt install python3-tk`).

## The Debug Tracker (GUI)

`python tracker_gui.py` opens a window that runs the tests for you and keeps track of your progress:

| Tab | What it gives you |
|---|---|
| **Dashboard** | Pass/fail counters, a progress bar, and a **pipeline map** - each stage turns green or red. Click a stage to jump to its tests. Also lists what you *fixed* and what you *broke* since the previous run. |
| **Test Board** | Every test with `PASS/FAIL`, file, function name, where the error was raised, and the error produced. Filter by status/file, search, click a row for the full failure text. **Log selected as bug** turns a failing test into a bug-log entry. |
| **Bug Log** | Your own debugging journal: title, module, category, status (Suspected -> Investigating -> Found -> Fixed), linked test (its live PASS/FAIL is shown), hypothesis, root cause, the fix, and what you learned. Saved in `tracker_data/`. |
| **History** | One line per run and a chart of the pass rate over time. |
| **Guide** | The data-flow diagram, a debugging method, and a checklist of where bugs like to hide. |

Tip: write the hypothesis in the Bug Log *before* you change code. Writing "root cause" and "what I learned" afterwards is what turns a fix into a skill.

## What the pipeline does

```
data/orders.csv      data/customers.csv      data/products.csv
        \                   |                      /
         1 INGEST   -> read, parse dates, validate rows, remove duplicates     pipeline/ingest.py
         2 CLEAN    -> normalise emails / countries / statuses, currency       pipeline/clean.py
         3 TRANSFORM-> join, coupons, revenue / cost / profit, first orders    pipeline/transform.py
         4 AGGREGATE-> totals, top customers, moving average, percentiles,     pipeline/aggregate.py
                       growth, retention
         5 STORE    -> SQLite upsert, watermark (incremental load), SQL queries pipeline/store.py
         6 REPORT   -> money / percent formatting, CSV text, run summary       pipeline/report.py

pipeline/runner.py runs the stages in order and returns a run summary.
pipeline/config.py holds the shared business rules (FX rates, coupons, statuses).
```

**The docstring at the top of every module is its specification.** Read it before you read the code.

### The data
`data/*.csv` is deliberately dirty: duplicate rows, mixed date formats (`2025-01-05` and day-first `05/01/2025`), upper/lower case and stray spaces, unknown customers and products, and rows that must be rejected. The end-to-end tests use these files.

## Ground rules

1. **Fix the code in `pipeline/`.** Do not edit `tests/`, `data/`, `config.py` or `tracker/`.
2. Do not delete or weaken tests. They are the specification.
3. Passing tests is necessary, not sufficient. Try your fix with inputs the tests do not use.
4. Where a test fails is not always where the bug is. The end-to-end tests can fail because of *any* earlier stage.
5. Commit after each fix so you can see (and undo) what you changed.

## Suggested way to work

1. Open the tracker. Look at the pipeline map: which stages are red?
2. Start with the **earliest** red stage and the **smallest** failing unit test.
3. Read the module docstring and the test. Run the function by hand with the test's input.
4. Log the bug, form a hypothesis, change one thing, re-run.
5. Watch the end-to-end tests: they should turn green *by themselves* as you fix upstream stages. If one is still red when everything else is green, there is a bug that only shows up when the stages work together.

## Project layout

```
pipeline/     the code you are debugging
data/         the three CSV files
tests/        the specification (do not edit)
tracker/      the GUI and its logic (do not edit)
tracker_gui.py   start the GUI
```
