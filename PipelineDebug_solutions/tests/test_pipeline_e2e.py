"""End-to-end tests: the whole pipeline, from CSV files to numbers in the database.

These use the real sample data in data/ (35 order rows: duplicates, mixed date formats, bad rows,
orphans, odd casing) and a few small generated files for the incremental-load scenarios.
"""
import sqlite3
from datetime import date
from pathlib import Path

import pytest

from pipeline.aggregate import (average_order_value, growth_rates, monthly_retention,
                                revenue_by_category, revenue_by_month, top_customers)
from pipeline.report import render_monthly_report, render_summary
from pipeline.runner import run_pipeline
from pipeline.store import get_watermark, sql_repeat_customers, sql_revenue_by_month, sql_top_countries
from tests.factories import ORDER_HEADER, write_csv

DATA = Path(__file__).resolve().parent.parent / "data"
ORDERS, CUSTOMERS, PRODUCTS = DATA / "orders.csv", DATA / "customers.csv", DATA / "products.csv"


@pytest.fixture
def conn():
    connection = sqlite3.connect(":memory:")
    yield connection
    connection.close()


@pytest.fixture
def loaded(conn):
    summary = run_pipeline(ORDERS, CUSTOMERS, PRODUCTS, conn)
    return conn, summary


def fact_rows(conn):
    conn.row_factory = sqlite3.Row
    rows = [dict(r) for r in conn.execute("SELECT * FROM fact_orders ORDER BY order_id")]
    conn.row_factory = None
    return rows


# ---- the run summary -----------------------------------------------------
def test_summary_counts(loaded):
    _, summary = loaded
    assert summary == {"read": 29, "rejected": 6, "duplicates_removed": 1, "orphans": 1,
                       "loaded": 27, "inserted": 27, "updated": 0, "watermark": "2025-02-27"}


def test_rejected_rows_are_logged_with_correct_line_numbers(conn, tmp_path):
    rejects = tmp_path / "rejects.csv"
    run_pipeline(ORDERS, CUSTOMERS, PRODUCTS, conn, rejects_path=rejects)
    assert rejects.read_text(encoding="utf-8").splitlines() == [
        "line,reason",
        "30,quantity must be at least 1",
        "31,quantity must be a whole number",
        "32,unknown currency: 'JPY'",
        "33,unrecognised date: '2025-13-45'",
        "34,unknown status: 'shipped'",
        "35,missing order_id",
    ]


def test_summary_renders(loaded):
    _, summary = loaded
    assert render_summary(summary).splitlines()[0] == "read: 29"


# ---- individual orders (checked by hand from the CSV) --------------------
def test_percent_coupon_and_currency(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1002"]["revenue_usd"] == 90.0            # 80 GBP - 10% = 72 GBP * 1.25


def test_flat_coupon_is_taken_in_order_currency(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1004"]["revenue_usd"] == 115.0           # 120 - 5
    assert o["O1011"]["revenue_usd"] == 27.0            # 4 * 8.00 = 32 - 5


def test_percent_coupon_on_the_whole_line(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1006"]["revenue_usd"] == 9.0             # 10 * 1.20 = 12 - 25%
    assert o["O1009"]["revenue_usd"] == 135.0           # 2 * 75 = 150 - 10%


def test_half_up_rounding_on_a_real_order(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1020"]["revenue_usd"] == 140.63          # 125 GBP - 10% = 112.5 * 1.25 = 140.625


def test_day_first_dates_are_read_correctly(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1018"]["order_date"] == "2025-01-05"
    assert o["O1023"]["order_date"] == "2025-02-03"


def test_duplicate_order_keeps_the_latest_version(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1005"]["status"] == "refunded"
    assert o["O1005"]["revenue_usd"] == 0.0


def test_lowercase_customer_id_still_joins(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1006"]["customer_id"] == "C005"
    assert o["O1006"]["country"] == "US"


def test_unknown_customer_and_orphan_product(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1017"]["country"] == "UNKNOWN"
    assert "O1019" not in o


def test_padded_order_id_is_cleaned(loaded):
    conn, _ = loaded
    assert "O1033" in {r["order_id"] for r in fact_rows(conn)}


def test_country_aliases_are_applied(loaded):
    conn, _ = loaded
    o = {r["order_id"]: r for r in fact_rows(conn)}
    assert o["O1001"]["country"] == "US"                # USA
    assert o["O1002"]["country"] == "GB"                # uk
    assert o["O1003"]["country"] == "DE"                # " de "
    assert o["O1005"]["country"] == "US"                # United States


def test_first_order_flags(loaded):
    conn, _ = loaded
    firsts = {r["order_id"] for r in fact_rows(conn) if r["is_first_order"]}
    assert firsts == {"O1001", "O1002", "O1003", "O1005", "O1006", "O1007", "O1011", "O1017"}


def test_non_completed_orders_earn_nothing(loaded):
    conn, _ = loaded
    for r in fact_rows(conn):
        if r["status"] != "completed":
            assert (r["revenue_usd"], r["cost_usd"], r["profit_usd"]) == (0.0, 0.0, 0.0)


# ---- aggregates over the whole data set ----------------------------------
def rows_for_analysis(conn):
    rows = fact_rows(conn)
    for r in rows:
        r["order_date"] = date.fromisoformat(r["order_date"])
    return rows


def test_revenue_by_month_total(loaded):
    conn, _ = loaded
    assert revenue_by_month(rows_for_analysis(conn)) == {
        "2024-11": 330.48, "2024-12": 290.75, "2025-01": 349.53, "2025-02": 178.3}


def test_sql_and_python_agree_on_monthly_revenue(loaded):
    conn, _ = loaded
    assert sql_revenue_by_month(conn) == revenue_by_month(rows_for_analysis(conn))


def test_revenue_by_category_total(loaded):
    conn, _ = loaded
    assert revenue_by_category(rows_for_analysis(conn)) == {
        "accessories": 99.45, "electronics": 739.38, "home": 198.88, "stationery": 111.35}


def test_top_countries(loaded):
    conn, _ = loaded
    assert sql_top_countries(conn, 3) == [("GB", 466.68), ("US", 465.7), ("UNKNOWN", 115.0)]


def test_top_customers(loaded):
    conn, _ = loaded
    assert top_customers(rows_for_analysis(conn), 3) == [("C001", 324.7), ("C002", 282.88),
                                                          ("C006", 183.8)]


def test_repeat_customers(loaded):
    conn, _ = loaded
    assert sql_repeat_customers(conn) == 6


def test_average_order_value(loaded):
    conn, _ = loaded
    assert average_order_value(rows_for_analysis(conn)) == 49.96


def test_growth_across_the_year_end(loaded):
    conn, _ = loaded
    growth = growth_rates(revenue_by_month(rows_for_analysis(conn)))
    assert growth == {"2024-11": None, "2024-12": -12.0, "2025-01": 20.2, "2025-02": -49.0}


def test_retention_across_the_year_end(loaded):
    conn, _ = loaded
    assert monthly_retention(rows_for_analysis(conn)) == {
        "2024-11": 0.8, "2024-12": 0.6, "2025-01": 0.2, "2025-02": None}


def test_monthly_report_text(loaded):
    conn, _ = loaded
    monthly = revenue_by_month(rows_for_analysis(conn))
    assert render_monthly_report(monthly, growth_rates(monthly)).splitlines() == [
        "2024-11  $330.48  n/a",
        "2024-12  $290.75  -12.0%",
        "2025-01  $349.53  +20.2%",
        "2025-02  $178.30  -49.0%",
    ]


# ---- re-runs and incremental loads ---------------------------------------
def test_running_twice_creates_no_duplicates(conn):
    run_pipeline(ORDERS, CUSTOMERS, PRODUCTS, conn)
    second = run_pipeline(ORDERS, CUSTOMERS, PRODUCTS, conn)
    assert conn.execute("SELECT COUNT(*) FROM fact_orders").fetchone()[0] == 27
    # only the orders on/after the watermark day are reloaded, and they are all updates
    assert second["loaded"] == 1
    assert (second["inserted"], second["updated"]) == (0, 1)
    assert second["watermark"] == "2025-02-27"


def test_incremental_run_loads_only_new_data(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date",
                          ["C1,A,a@x.com,us,2024-01-01"])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", ["P1,Pen,home,1.00"])
    day1 = write_csv(tmp_path / "o1.csv", ORDER_HEADER, [
        "O1,C1,P1,1,10.00,USD,2025-01-01,completed,",
        "O2,C1,P1,1,10.00,USD,2025-01-02,completed,",
    ])
    first = run_pipeline(day1, customers, products, conn)
    assert (first["inserted"], first["updated"], first["watermark"]) == (2, 0, "2025-01-02")

    day2 = write_csv(tmp_path / "o2.csv", ORDER_HEADER, [
        "O1,C1,P1,1,10.00,USD,2025-01-01,completed,",
        "O2,C1,P1,1,10.00,USD,2025-01-02,completed,",
        "O3,C1,P1,1,10.00,USD,2025-01-03,completed,",
    ])
    second = run_pipeline(day2, customers, products, conn)
    assert second["loaded"] == 2                        # O2 (watermark day) and O3
    assert (second["inserted"], second["updated"]) == (1, 1)
    assert second["watermark"] == "2025-01-03"


def test_late_arriving_order_on_the_watermark_day_is_not_lost(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date",
                          ["C1,A,a@x.com,us,2024-01-01"])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", ["P1,Pen,home,1.00"])
    first = write_csv(tmp_path / "o1.csv", ORDER_HEADER, ["O1,C1,P1,1,10.00,USD,2025-01-05,completed,"])
    run_pipeline(first, customers, products, conn)

    later = write_csv(tmp_path / "o2.csv", ORDER_HEADER, [
        "O1,C1,P1,1,10.00,USD,2025-01-05,completed,",
        "O7,C1,P1,2,10.00,USD,2025-01-05,completed,",   # arrived late, same day as the watermark
    ])
    run_pipeline(later, customers, products, conn)
    ids = [r[0] for r in conn.execute("SELECT order_id FROM fact_orders ORDER BY order_id")]
    assert ids == ["O1", "O7"]


def test_older_data_does_not_move_the_watermark_back(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date",
                          ["C1,A,a@x.com,us,2024-01-01"])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", ["P1,Pen,home,1.00"])
    new = write_csv(tmp_path / "new.csv", ORDER_HEADER, ["O2,C1,P1,1,10.00,USD,2025-03-01,completed,"])
    run_pipeline(new, customers, products, conn)
    old = write_csv(tmp_path / "old.csv", ORDER_HEADER, ["O1,C1,P1,1,10.00,USD,2025-03-01,completed,"])
    run_pipeline(old, customers, products, conn)
    assert get_watermark(conn) == date(2025, 3, 1)


def test_status_change_is_picked_up_on_the_next_run(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date",
                          ["C1,A,a@x.com,us,2024-01-01"])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", ["P1,Pen,home,1.00"])
    v1 = write_csv(tmp_path / "v1.csv", ORDER_HEADER, ["O1,C1,P1,1,10.00,USD,2025-01-05,pending,"])
    run_pipeline(v1, customers, products, conn)
    v2 = write_csv(tmp_path / "v2.csv", ORDER_HEADER, ["O1,C1,P1,1,10.00,USD,2025-01-05,completed,"])
    run_pipeline(v2, customers, products, conn)
    assert conn.execute("SELECT status, revenue_usd FROM fact_orders").fetchone() == ("completed", 10.0)


def test_empty_orders_file(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date", [])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", [])
    orders = write_csv(tmp_path / "o.csv", ORDER_HEADER, [])
    summary = run_pipeline(orders, customers, products, conn)
    assert summary["loaded"] == 0 and summary["watermark"] is None


def test_custom_fx_rates_are_used(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date",
                          ["C1,A,a@x.com,us,2024-01-01"])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", ["P1,Pen,home,1.00"])
    orders = write_csv(tmp_path / "o.csv", ORDER_HEADER, ["O1,C1,P1,1,10.00,EUR,2025-01-05,completed,"])
    run_pipeline(orders, customers, products, conn, rates={"USD": 1.0, "EUR": 2.0})
    assert conn.execute("SELECT revenue_usd FROM fact_orders").fetchone()[0] == 20.0


def test_first_order_flag_survives_an_incremental_run(conn, tmp_path):
    customers = write_csv(tmp_path / "c.csv", "customer_id,name,email,country,signup_date",
                          ["C1,A,a@x.com,us,2024-01-01"])
    products = write_csv(tmp_path / "p.csv", "product_id,name,category,cost", ["P1,Pen,home,1.00"])
    day2 = write_csv(tmp_path / "o1.csv", ORDER_HEADER, [
        "O1,C1,P1,1,10.00,USD,2025-01-01,completed,",
        "O2,C1,P1,1,10.00,USD,2025-01-02,completed,",
    ])
    run_pipeline(day2, customers, products, conn)
    day3 = write_csv(tmp_path / "o2.csv", ORDER_HEADER, [
        "O1,C1,P1,1,10.00,USD,2025-01-01,completed,",
        "O2,C1,P1,1,10.00,USD,2025-01-02,completed,",
        "O3,C1,P1,1,10.00,USD,2025-01-03,completed,",
    ])
    run_pipeline(day3, customers, products, conn)
    flags = dict(conn.execute("SELECT order_id, is_first_order FROM fact_orders"))
    assert flags == {"O1": 1, "O2": 0, "O3": 0}
