from datetime import date

import pytest

from pipeline.transform import enrich_orders, line_total, mark_first_orders
from tests.factories import make_customer, make_order, make_product, make_row

RATES = {"USD": 1.0, "EUR": 1.10, "GBP": 1.25}


# ---- line_total ----------------------------------------------------------
def test_line_total_no_coupon():
    assert line_total(make_order(quantity=3, unit_price=4.5)) == 13.5


def test_line_total_percent_coupon_applies_to_whole_line():
    assert line_total(make_order(quantity=3, unit_price=10.0, coupon="SAVE10")) == 27.0


def test_line_total_percent_25():
    assert line_total(make_order(quantity=2, unit_price=40.0, coupon="SAVE25")) == 60.0


def test_line_total_flat_coupon_takes_off_the_line():
    assert line_total(make_order(quantity=2, unit_price=20.0, coupon="FLAT5")) == 35.0


def test_line_total_flat_coupon_never_below_zero():
    assert line_total(make_order(quantity=1, unit_price=3.0, coupon="FLAT5")) == 0.0


def test_line_total_unknown_coupon_is_ignored():
    assert line_total(make_order(quantity=2, unit_price=5.0, coupon="BOGUS")) == 10.0


def test_line_total_rounds_to_four_decimals():
    assert line_total(make_order(quantity=1, unit_price=1.0 / 3)) == 0.3333


# ---- enrich_orders: joins ------------------------------------------------
def enrich(orders, customers=None, products=None):
    customers = customers if customers is not None else [make_customer()]
    products = products if products is not None else [make_product()]
    return enrich_orders(orders, customers, products, RATES)


def test_enrich_basic_row():
    rows, orphans = enrich([make_order(quantity=2, unit_price=10.0)])
    assert orphans == []
    (row,) = rows
    assert row["country"] == "US"
    assert row["category"] == "home"
    assert row["month"] == "2025-01"
    assert row["revenue_usd"] == 20.0
    assert row["cost_usd"] == 8.0
    assert row["profit_usd"] == 12.0


def test_enrich_unknown_customer_gets_unknown_country():
    rows, _ = enrich([make_order(customer_id="C404")])
    assert rows[0]["country"] == "UNKNOWN"


def test_enrich_unknown_product_is_an_orphan_and_not_in_rows():
    rows, orphans = enrich([make_order(order_id="O9", product_id="P404"), make_order(order_id="O1")])
    assert orphans == ["O9"]
    assert [r["order_id"] for r in rows] == ["O1"]


def test_enrich_keeps_input_order():
    orders = [make_order(order_id="O3"), make_order(order_id="O1"), make_order(order_id="O2")]
    rows, _ = enrich(orders)
    assert [r["order_id"] for r in rows] == ["O3", "O1", "O2"]


# ---- enrich_orders: money ------------------------------------------------
def test_enrich_converts_currency():
    rows, _ = enrich([make_order(unit_price=100.0, currency="GBP")])
    assert rows[0]["revenue_usd"] == 125.0


def test_enrich_percent_coupon_with_quantity():
    rows, _ = enrich([make_order(quantity=3, unit_price=10.0, coupon="SAVE10")])
    assert rows[0]["revenue_usd"] == 27.0


def test_enrich_flat_coupon_is_in_the_order_currency():
    # 30 GBP - 5 GBP = 25 GBP -> 31.25 USD (not 30*1.25 - 5 = 32.50)
    rows, _ = enrich([make_order(unit_price=30.0, currency="GBP", coupon="FLAT5")])
    assert rows[0]["revenue_usd"] == 31.25


def test_enrich_rounds_revenue_half_up():
    rows, _ = enrich([make_order(unit_price=112.5, currency="GBP")])
    assert rows[0]["revenue_usd"] == 140.63


@pytest.mark.parametrize("status", ["refunded", "cancelled", "pending"])
def test_enrich_only_completed_orders_earn_money(status):
    rows, _ = enrich([make_order(status=status, quantity=5, unit_price=20.0)])
    assert rows[0]["revenue_usd"] == 0.0
    assert rows[0]["cost_usd"] == 0.0
    assert rows[0]["profit_usd"] == 0.0


def test_enrich_cost_rounds_to_two_decimals():
    rows, _ = enrich([make_order(quantity=3)], products=[make_product(cost=1.005)])
    assert rows[0]["cost_usd"] == round(3 * 1.005, 2)


def test_enrich_profit_can_be_negative():
    rows, _ = enrich([make_order(unit_price=1.0)], products=[make_product(cost=4.0)])
    assert rows[0]["profit_usd"] == -3.0


# ---- first order flag ----------------------------------------------------
def test_first_order_is_the_earliest_by_date_not_by_position():
    rows = [
        make_row(order_id="O2", order_date=date(2025, 2, 1)),
        make_row(order_id="O1", order_date=date(2025, 1, 1)),
    ]
    mark_first_orders(rows)
    assert [r["is_first_order"] for r in rows] == [False, True]


def test_first_order_tie_on_date_goes_to_smaller_order_id():
    rows = [
        make_row(order_id="O9", order_date=date(2025, 1, 1)),
        make_row(order_id="O2", order_date=date(2025, 1, 1)),
    ]
    mark_first_orders(rows)
    assert [r["is_first_order"] for r in rows] == [False, True]


def test_first_order_is_per_customer():
    rows = [
        make_row(order_id="O1", customer_id="A", order_date=date(2025, 1, 5)),
        make_row(order_id="O2", customer_id="B", order_date=date(2025, 1, 6)),
        make_row(order_id="O3", customer_id="A", order_date=date(2025, 1, 7)),
    ]
    mark_first_orders(rows)
    assert [r["is_first_order"] for r in rows] == [True, True, False]


def test_first_order_counts_even_if_not_completed():
    rows = [
        make_row(order_id="O1", status="cancelled", order_date=date(2025, 1, 1)),
        make_row(order_id="O2", order_date=date(2025, 1, 2)),
    ]
    mark_first_orders(rows)
    assert [r["is_first_order"] for r in rows] == [True, False]


def test_enrich_sets_first_order_flag():
    orders = [
        make_order(order_id="O2", order_date=date(2025, 3, 1)),
        make_order(order_id="O1", order_date=date(2025, 2, 1)),
    ]
    rows, _ = enrich(orders)
    assert {r["order_id"]: r["is_first_order"] for r in rows} == {"O2": False, "O1": True}
