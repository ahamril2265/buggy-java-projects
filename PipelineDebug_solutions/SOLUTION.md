# PipelineDebug - answer key (24 bugs)

**Do not open this until you have tried.** The exercise starts at 141 passing / 71 failing out of 212 tests.
The folder `PipelineDebug_solutions/` holds the correct code (all 212 pass).

`tests` = which test files notice the bug. **e2e-only** means no unit test can see it: only running the whole pipeline exposes it.

---

## Stage 1 - ingest (`ingest.py`)

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B01 | `parse_date` reads `dd/mm/yyyy` with the format `%m/%d/%Y`. `05/01/2025` becomes 1 May, and `25/12/2024` raises (so the row is rejected). | Use `%d/%m/%Y`. | Ambiguous dates are *silent* whenever the day is 12 or less. Test with a day above 12 **and** a day below 12. | ingest, clean, e2e |
| B02 | `load_orders` numbers lines with `index + 1`. The header is line 1, so the first data row is line 2. | `index + 2`. | Off-by-one in a report someone else will use to find the bad row. | ingest, e2e |
| B03 | `dedupe_orders` keeps the **first** version of an order. Updates arrive as *later* rows, so refunds/status changes are lost. | Keep the last: `latest[id] = order` every time. | "Latest record wins" (change data capture). | ingest, e2e |
| B04 | `parse_order_row` does not upper-case `customer_id`. `c005` never joins to `C005`, so the order gets country `UNKNOWN` and the customer looks new. | `.strip().upper()`. | Normalise **keys** before joining. A failed join does not raise, it silently loses data. | ingest, e2e |

## Stage 2 - clean (`clean.py`)

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B05 | `to_usd` uses `round(float * rate, 2)`. Python's `round` is banker's rounding *and* works on binary floats, so 2.675 gives 2.67 and 140.625 gives 140.62. | `Decimal(str(amount)) * Decimal(str(rate))` then `quantize(Decimal("0.01"), ROUND_HALF_UP)`. | Money is not a float. Rounding mode is part of the spec. | clean, transform, e2e |
| B06 | `normalize_country` looks in the alias table (upper-case keys) **before** upper-casing. `usa`, `United States`, `uk` miss the table. | Upper-case first, then look up. | Order of operations in cleaning. Only *lower-case* input fails, so some tests pass. | clean, e2e |
| B07 | `normalize_status` validates against the allowed list **before** mapping `canceled` to `cancelled`, so a valid status is rejected as unknown. | Map first, validate second. | Same theme as B06: normalise, *then* check. | clean, e2e |

## Stage 3 - transform (`transform.py`)

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B08 | `line_total`: the flat coupon is subtracted **per unit** (`value * quantity`) instead of once per line. Invisible when quantity is 1. | `gross - value`. | Read the spec's *unit* of a value. Test with quantity above 1. | transform, e2e |
| B09 | Refunded orders earn revenue and cost (`status in ("completed", "refunded")`). | Only `"completed"`. | Business rule: money only for completed orders. | transform, e2e |
| B10 | `mark_first_orders` walks the rows in input order instead of sorting by `(order_date, order_id)`. The answer depends on how the file happened to be sorted. | Iterate over `sorted(rows, key=...)`. | Never rely on input order unless the spec says so. | transform |

## Stage 4 - aggregate (`aggregate.py`)

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B11 | `moving_average` slices `values[i - window : i + 1]`, which is `window + 1` values. | `values[max(0, i - window + 1) : i + 1]`. | Slice end is exclusive; count the elements. | aggregate |
| B12 | `percentile` weights the interpolation with `(high - rank)` instead of `(rank - low)`. Median tests pass **by symmetry** (weight 0.5) and whole-number ranks; p90 is wrong. | `* (rank - low)`. | A test with only "nice" inputs can hide a wrong formula. | aggregate |
| B13 | `growth_rates` divides by the **current** month instead of the previous one. | Divide by `previous`. | Growth is relative to the base period. | aggregate, e2e |
| B14 | `top_customers` sorts by revenue only. Ties keep dict insertion order (first appearance in the data). | `key=(-revenue, customer_id)`. | Sorting needs a total order to be deterministic. | aggregate |
| B15 | `next_month("2024-12")` returns `2024-01`: the year is not incremented. | `f"{year + 1}-01"`. | Year-end boundary; retention silently drops to 0 for December. | aggregate, e2e |
| B16 | `average_order_value` averages **all** rows, including refunded/pending rows that have revenue 0. | Only completed rows in the list. | Denominator must match the population in the spec. | aggregate, e2e |

## Stage 5 - store (`store.py`)

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B17 | `upsert_orders` checks "did this row exist?" **after** running the upsert, so it always exists: everything is reported as an update, never an insert. | Check *before* the write. | Ordering of a check and the action it describes. | store, e2e |
| B18 | `set_watermark` no longer refuses an older date, so loading an old file moves the watermark **backwards** and the next run reprocesses (or skips) the wrong data. | Ignore a date that is `<=` the stored one. | A watermark is a high-water mark: monotonic. | store |
| B20 | `sql_repeat_customers` uses `HAVING COUNT(*) > 2`. "Two or more" is `>= 2`. | `>= 2`. | Off-by-one in SQL; `HAVING` filters groups. | store, e2e |
| B21 | `sql_top_countries` breaks ties with `country DESC`; the spec says alphabetical (ASC). | `country ASC`. | Deterministic ORDER BY. | store |

## Stage 6 - report (`report.py`)

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B22 | `format_money(-5)` gives `$-5.00`. The minus belongs before the dollar sign. | `sign + "$" + f"{abs(amount):,.2f}"`. | Formatting rules for negatives. | report |

## The orchestrator (`runner.py`) - integration bugs

| # | Bug | Fix | Concept | Tests |
|---|---|---|---|---|
| B19 | The incremental filter uses `order_date > watermark`. Orders that arrive late **on the watermark day** are never loaded. | `>=` (the upsert makes reloading that day safe). | Incremental load boundaries and late-arriving data. | **e2e-only** |
| B23 | `enrich_orders(...)` is called **without** the `rates` argument, so the runner silently ignores a caller's FX rates and uses the default. | Pass `rates` through. | A parameter that is accepted but not used. Unit tests of each stage cannot see this. | **e2e-only** |
| B24 | The runner drops old orders **before** `enrich_orders`. The "first order" flag is then computed only over the recent slice, so on an incremental run an old customer's latest-slice order is flagged as their first and overwrites the correct value in the database. | Enrich everything, **then** filter (`fresh = [row for row in rows if ...]`). | Derived values that depend on the whole history must be computed before you cut the data. | **e2e-only** |

---

## Bugs that hide other bugs

* **B04 (ID case) + B06/B07 (cleaning order) + B01 (dates)** all change the *same* end-to-end numbers (country totals, retention, monthly revenue). Fixing one shows the next.
* **B09 (refund revenue) + B16 (average)** both move the average order value.
* **B24 and B19** are both in the incremental filter. Fixing the boundary (B19) and forgetting the order of enrich/filter (B24) leaves the `test_first_order_flag_survives_an_incremental_run` test red.
* **B13, B15, B03** all feed `test_monthly_report_text` / `test_retention_across_the_year_end`. That test only turns green when all of them are fixed.

## Suggested fixing order

Follow the data: B01-B04 (ingest), B05-B07 (clean), B08-B10 (transform), B11-B16 (aggregate), B17-B21 (store, SQL), B22 (report), then B19/B23/B24 in the runner. After each stage, the end-to-end tests that are still red belong to a *later* stage.

## Things you should now be able to explain

1. Why `round(2.675, 2)` is 2.67 and how `Decimal` fixes it.
2. What a watermark is, why it must move only forward, and why a load reprocesses the boundary day.
3. What "idempotent" means for a load, and how an upsert achieves it.
4. Why joins fail silently, and why keys are normalised before joining.
5. Why derived columns (like "first order") must be computed before you filter the data.
6. Why a test with only "nice" inputs (median of an even list, a quantity of 1) can pass while the formula is wrong.
