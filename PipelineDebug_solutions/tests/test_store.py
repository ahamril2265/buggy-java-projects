import sqlite3
from datetime import date

import pytest

from pipeline.store import (get_watermark, init_db, set_watermark, sql_repeat_customers,
                            sql_revenue_by_month, sql_top_countries, upsert_orders)
from tests.factories import make_row


@pytest.fixture
def conn():
    connection = sqlite3.connect(":memory:")
    init_db(connection)
    yield connection
    connection.close()


def count(conn):
    return conn.execute("SELECT COUNT(*) FROM fact_orders").fetchone()[0]


# ---- init_db -------------------------------------------------------------
def test_init_db_is_idempotent(conn):
    init_db(conn)
    init_db(conn)
    tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    assert {"fact_orders", "meta"} <= tables


# ---- upsert_orders -------------------------------------------------------
def test_upsert_inserts_new_rows(conn):
    assert upsert_orders(conn, [make_row(order_id="O1"), make_row(order_id="O2")]) == (2, 0)
    assert count(conn) == 2


def test_upsert_same_rows_twice_is_idempotent(conn):
    rows = [make_row(order_id="O1"), make_row(order_id="O2")]
    assert upsert_orders(conn, rows) == (2, 0)
    assert upsert_orders(conn, rows) == (0, 2)
    assert count(conn) == 2


def test_upsert_updates_the_existing_row(conn):
    upsert_orders(conn, [make_row(order_id="O1", status="pending", revenue_usd=0.0)])
    upsert_orders(conn, [make_row(order_id="O1", status="completed", revenue_usd=42.0)])
    status, revenue = conn.execute("SELECT status, revenue_usd FROM fact_orders").fetchone()
    assert (status, revenue) == ("completed", 42.0)


def test_upsert_mixed_new_and_existing(conn):
    upsert_orders(conn, [make_row(order_id="O1")])
    assert upsert_orders(conn, [make_row(order_id="O1"), make_row(order_id="O2"),
                                make_row(order_id="O3")]) == (2, 1)


def test_upsert_stores_date_text_and_flag_int(conn):
    upsert_orders(conn, [make_row(order_id="O1", order_date=date(2025, 3, 9), is_first_order=True),
                         make_row(order_id="O2", is_first_order=False)])
    rows = dict((r[0], r[1:]) for r in conn.execute(
        "SELECT order_id, order_date, is_first_order FROM fact_orders"))
    assert rows["O1"] == ("2025-03-09", 1)
    assert rows["O2"][1] == 0


def test_upsert_empty_list(conn):
    assert upsert_orders(conn, []) == (0, 0)


# ---- watermark -----------------------------------------------------------
def test_watermark_starts_empty(conn):
    assert get_watermark(conn) is None


def test_watermark_roundtrip_returns_a_date(conn):
    set_watermark(conn, date(2025, 1, 31))
    assert get_watermark(conn) == date(2025, 1, 31)


def test_watermark_moves_forward(conn):
    set_watermark(conn, date(2025, 1, 31))
    set_watermark(conn, date(2025, 2, 15))
    assert get_watermark(conn) == date(2025, 2, 15)


def test_watermark_never_moves_backward(conn):
    set_watermark(conn, date(2025, 2, 15))
    set_watermark(conn, date(2025, 1, 1))
    assert get_watermark(conn) == date(2025, 2, 15)


def test_watermark_same_day_keeps_value(conn):
    set_watermark(conn, date(2025, 2, 15))
    set_watermark(conn, date(2025, 2, 15))
    assert get_watermark(conn) == date(2025, 2, 15)


# ---- SQL queries ---------------------------------------------------------
def test_sql_revenue_by_month(conn):
    upsert_orders(conn, [
        make_row(order_id="O1", month="2025-02", revenue_usd=10.0),
        make_row(order_id="O2", month="2025-01", revenue_usd=0.1),
        make_row(order_id="O3", month="2025-01", revenue_usd=0.2),
    ])
    result = sql_revenue_by_month(conn)
    assert list(result.items()) == [("2025-01", 0.3), ("2025-02", 10.0)]


def test_sql_revenue_by_month_ignores_unearned_orders(conn):
    upsert_orders(conn, [
        make_row(order_id="O1", month="2025-01", revenue_usd=10.0),
        make_row(order_id="O2", month="2025-01", status="refunded", revenue_usd=0.0),
    ])
    assert sql_revenue_by_month(conn) == {"2025-01": 10.0}


def test_sql_top_countries_order_and_limit(conn):
    upsert_orders(conn, [
        make_row(order_id="O1", country="US", revenue_usd=50.0),
        make_row(order_id="O2", country="GB", revenue_usd=80.0),
        make_row(order_id="O3", country="DE", revenue_usd=20.0),
        make_row(order_id="O4", country="US", revenue_usd=40.0),
    ])
    assert sql_top_countries(conn, 2) == [("US", 90.0), ("GB", 80.0)]
    assert len(sql_top_countries(conn, 1)) == 1
    assert len(sql_top_countries(conn, 10)) == 3


def test_sql_top_countries_tie_is_alphabetical(conn):
    upsert_orders(conn, [
        make_row(order_id="O1", country="US", revenue_usd=10.0),
        make_row(order_id="O2", country="FR", revenue_usd=10.0),
        make_row(order_id="O3", country="DE", revenue_usd=10.0),
    ])
    assert sql_top_countries(conn, 2) == [("DE", 10.0), ("FR", 10.0)]


def test_sql_repeat_customers_needs_two_completed_orders(conn):
    upsert_orders(conn, [
        make_row(order_id="O1", customer_id="A"), make_row(order_id="O2", customer_id="A"),
        make_row(order_id="O3", customer_id="B"),
        make_row(order_id="O4", customer_id="C"), make_row(order_id="O5", customer_id="C"),
        make_row(order_id="O6", customer_id="C"),
    ])
    assert sql_repeat_customers(conn) == 2


def test_sql_repeat_customers_ignores_non_completed(conn):
    upsert_orders(conn, [
        make_row(order_id="O1", customer_id="A"),
        make_row(order_id="O2", customer_id="A", status="refunded"),
    ])
    assert sql_repeat_customers(conn) == 0
