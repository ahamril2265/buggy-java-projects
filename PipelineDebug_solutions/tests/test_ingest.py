from datetime import date

import pytest

from pipeline.ingest import dedupe_orders, load_orders, parse_date, parse_order_row, read_csv
from tests.factories import ORDER_HEADER, make_order, write_csv


def raw(**overrides):
    row = {
        "order_id": "o1", "customer_id": "c1", "product_id": "p1", "quantity": "2",
        "unit_price": "9.99", "currency": "eur", "order_date": "2025-01-15",
        "status": "Completed", "coupon": "save10",
    }
    row.update(overrides)
    return row


# ---- read_csv ------------------------------------------------------------
def test_read_csv_strips_headers_and_cells(tmp_path):
    path = tmp_path / "x.csv"
    path.write_text(" a , b \n  1 ,2  \n", encoding="utf-8")
    assert read_csv(path) == [{"a": "1", "b": "2"}]


def test_read_csv_empty_cell_is_empty_string(tmp_path):
    path = tmp_path / "x.csv"
    path.write_text("a,b\n1,\n", encoding="utf-8")
    assert read_csv(path) == [{"a": "1", "b": ""}]


# ---- parse_date ----------------------------------------------------------
def test_parse_date_iso():
    assert parse_date("2025-01-05") == date(2025, 1, 5)


def test_parse_date_european_day_first():
    assert parse_date("05/01/2025") == date(2025, 1, 5)


def test_parse_date_european_day_over_twelve():
    assert parse_date("25/12/2024") == date(2024, 12, 25)


def test_parse_date_european_february():
    assert parse_date("03/02/2025") == date(2025, 2, 3)


def test_parse_date_ignores_surrounding_space():
    assert parse_date("  2025-03-09 ") == date(2025, 3, 9)


@pytest.mark.parametrize("text", ["", "yesterday", "2025-13-45", "2025/01/05", "31/02/2025"])
def test_parse_date_rejects_garbage(text):
    with pytest.raises(ValueError):
        parse_date(text)


# ---- parse_order_row -----------------------------------------------------
def test_parse_order_row_normalises_everything():
    order = parse_order_row(raw())
    assert order == {
        "order_id": "O1", "customer_id": "C1", "product_id": "P1", "quantity": 2,
        "unit_price": 9.99, "currency": "EUR", "order_date": date(2025, 1, 15),
        "status": "completed", "coupon": "SAVE10",
    }


def test_parse_order_row_empty_coupon_is_empty_string():
    assert parse_order_row(raw(coupon=""))["coupon"] == ""


def test_parse_order_row_missing_order_id():
    with pytest.raises(ValueError, match="order_id"):
        parse_order_row(raw(order_id="  "))


@pytest.mark.parametrize("quantity", ["0", "-2"])
def test_parse_order_row_quantity_must_be_positive(quantity):
    with pytest.raises(ValueError, match="at least 1"):
        parse_order_row(raw(quantity=quantity))


@pytest.mark.parametrize("quantity", ["", "two", "1.5"])
def test_parse_order_row_quantity_must_be_whole(quantity):
    with pytest.raises(ValueError, match="whole number"):
        parse_order_row(raw(quantity=quantity))


def test_parse_order_row_price_zero_is_allowed():
    assert parse_order_row(raw(unit_price="0"))["unit_price"] == 0.0


def test_parse_order_row_negative_price():
    with pytest.raises(ValueError, match="negative"):
        parse_order_row(raw(unit_price="-1"))


def test_parse_order_row_price_not_a_number():
    with pytest.raises(ValueError, match="number"):
        parse_order_row(raw(unit_price="free"))


def test_parse_order_row_unknown_currency():
    with pytest.raises(ValueError, match="currency"):
        parse_order_row(raw(currency="JPY"))


def test_parse_order_row_unknown_status():
    with pytest.raises(ValueError, match="status"):
        parse_order_row(raw(status="shipped"))


def test_parse_order_row_bad_date():
    with pytest.raises(ValueError, match="date"):
        parse_order_row(raw(order_date="2025-13-45"))


# ---- load_orders ---------------------------------------------------------
def test_load_orders_valid_and_rejected_lines(tmp_path):
    path = write_csv(tmp_path / "o.csv", ORDER_HEADER, [
        "O1,C1,P1,1,5.00,USD,2025-01-01,completed,",   # line 2
        "O2,C1,P1,0,5.00,USD,2025-01-02,completed,",   # line 3 - bad quantity
        "O3,C1,P1,1,5.00,USD,2025-01-03,completed,",   # line 4
        "O4,C1,P1,1,5.00,XXX,2025-01-04,completed,",   # line 5 - bad currency
    ])
    orders, rejected = load_orders(path)
    assert [o["order_id"] for o in orders] == ["O1", "O3"]
    assert [r["line"] for r in rejected] == [3, 5]
    assert "at least 1" in rejected[0]["reason"]
    assert "currency" in rejected[1]["reason"]


def test_load_orders_first_data_row_is_line_two(tmp_path):
    path = write_csv(tmp_path / "o.csv", ORDER_HEADER, ["O1,C1,P1,x,5.00,USD,2025-01-01,completed,"])
    _, rejected = load_orders(path)
    assert rejected[0]["line"] == 2


def test_load_orders_keeps_file_order_and_duplicates(tmp_path):
    path = write_csv(tmp_path / "o.csv", ORDER_HEADER, [
        "O2,C1,P1,1,5.00,USD,2025-01-01,completed,",
        "O1,C1,P1,1,5.00,USD,2025-01-01,completed,",
        "O2,C1,P1,1,5.00,USD,2025-01-01,refunded,",
    ])
    orders, _ = load_orders(path)
    assert [o["order_id"] for o in orders] == ["O2", "O1", "O2"]


def test_load_orders_empty_file(tmp_path):
    path = write_csv(tmp_path / "o.csv", ORDER_HEADER, [])
    assert load_orders(path) == ([], [])


# ---- dedupe_orders -------------------------------------------------------
def test_dedupe_orders_keeps_the_last_occurrence():
    first = make_order(order_id="O1", status="completed")
    second = make_order(order_id="O1", status="refunded")
    assert dedupe_orders([first, second]) == [second]


def test_dedupe_orders_sorts_by_order_id():
    orders = [make_order(order_id="O3"), make_order(order_id="O1"), make_order(order_id="O2")]
    assert [o["order_id"] for o in dedupe_orders(orders)] == ["O1", "O2", "O3"]


def test_dedupe_orders_three_versions_last_wins():
    versions = [make_order(order_id="O1", quantity=q) for q in (1, 2, 3)]
    assert dedupe_orders(versions)[0]["quantity"] == 3


def test_dedupe_orders_empty():
    assert dedupe_orders([]) == []
