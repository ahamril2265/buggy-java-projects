from datetime import date

import pytest

from pipeline.clean import (clean_customers, clean_products, normalize_country, normalize_email,
                            normalize_status, to_usd)

RATES = {"USD": 1.0, "EUR": 1.10, "GBP": 1.25}


# ---- normalize_email -----------------------------------------------------
def test_normalize_email_lowercases_and_strips():
    assert normalize_email("  Alice@Example.COM ") == "alice@example.com"


# ---- normalize_country ---------------------------------------------------
@pytest.mark.parametrize("raw,expected", [
    ("US", "US"), ("usa", "US"), (" USA ", "US"), ("United States", "US"),
    ("uk", "GB"), ("United Kingdom", "GB"), ("gb", "GB"),
    ("de", "DE"), (" fr ", "FR"), ("", ""),
])
def test_normalize_country(raw, expected):
    assert normalize_country(raw) == expected


# ---- normalize_status ----------------------------------------------------
@pytest.mark.parametrize("raw,expected", [
    ("completed", "completed"), ("COMPLETED", "completed"), (" Refunded ", "refunded"),
    ("cancelled", "cancelled"), ("canceled", "cancelled"), ("CANCELED", "cancelled"),
    ("pending", "pending"),
])
def test_normalize_status(raw, expected):
    assert normalize_status(raw) == expected


@pytest.mark.parametrize("raw", ["", "shipped", "done"])
def test_normalize_status_rejects_unknown(raw):
    with pytest.raises(ValueError):
        normalize_status(raw)


# ---- to_usd --------------------------------------------------------------
def test_to_usd_usd_is_unchanged():
    assert to_usd(12.34, "USD", RATES) == 12.34


def test_to_usd_converts_with_the_rate():
    assert to_usd(100, "GBP", RATES) == 125.0
    assert to_usd(10, "EUR", RATES) == 11.0


def test_to_usd_rounds_half_up_not_bankers():
    assert to_usd(2.675, "USD", RATES) == 2.68


def test_to_usd_half_up_after_conversion():
    assert to_usd(112.5, "GBP", RATES) == 140.63       # 140.625 exactly


def test_to_usd_half_up_small_values():
    assert to_usd(0.005, "USD", RATES) == 0.01
    assert to_usd(0.015, "USD", RATES) == 0.02


def test_to_usd_rounds_down_below_half():
    assert to_usd(1.004, "USD", RATES) == 1.0


def test_to_usd_unknown_currency():
    with pytest.raises(ValueError, match="currency"):
        to_usd(5, "JPY", RATES)


def test_to_usd_uses_default_rates():
    assert to_usd(100, "EUR") == 110.0


# ---- clean_customers -----------------------------------------------------
def customer_row(**overrides):
    row = {"customer_id": "c1", "name": " Ann ", "email": " ANN@X.COM ", "country": "usa",
           "signup_date": "05/10/2024"}
    row.update(overrides)
    return row


def test_clean_customers_normalises_fields():
    (customer,) = clean_customers([customer_row()])
    assert customer == {"customer_id": "C1", "name": "Ann", "email": "ann@x.com", "country": "US",
                        "signup_date": date(2024, 10, 5)}


def test_clean_customers_sorted_by_id():
    rows = [customer_row(customer_id="C3"), customer_row(customer_id="C1"), customer_row(customer_id="C2")]
    assert [c["customer_id"] for c in clean_customers(rows)] == ["C1", "C2", "C3"]


def test_clean_customers_first_duplicate_wins():
    rows = [customer_row(name="First"), customer_row(customer_id="C1", name="Second")]
    result = clean_customers(rows)
    assert len(result) == 1
    assert result[0]["name"] == "First"


def test_clean_customers_drops_rows_without_id():
    assert clean_customers([customer_row(customer_id="  ")]) == []


# ---- clean_products ------------------------------------------------------
def product_row(**overrides):
    row = {"product_id": "p1", "name": "Pen", "category": " Stationery ", "cost": "0.50"}
    row.update(overrides)
    return row


def test_clean_products_normalises_fields():
    (product,) = clean_products([product_row()])
    assert product == {"product_id": "P1", "name": "Pen", "category": "stationery", "cost": 0.5}


def test_clean_products_first_duplicate_wins():
    rows = [product_row(cost="1.00"), product_row(product_id="P1", cost="9.00")]
    (product,) = clean_products(rows)
    assert product["cost"] == 1.0


def test_clean_products_sorted_and_drops_blank_ids():
    rows = [product_row(product_id="p2"), product_row(product_id=""), product_row(product_id="p1")]
    assert [p["product_id"] for p in clean_products(rows)] == ["P1", "P2"]
