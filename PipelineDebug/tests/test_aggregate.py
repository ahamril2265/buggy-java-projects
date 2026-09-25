from datetime import date

import pytest

from pipeline.aggregate import (average_order_value, group_sum, growth_rates, monthly_retention,
                                moving_average, next_month, percentile, revenue_by_category,
                                revenue_by_month, top_customers)
from tests.factories import make_row


# ---- group_sum & friends -------------------------------------------------
def test_group_sum_sorted_and_rounded():
    rows = [make_row(month="2025-02", revenue_usd=0.1), make_row(month="2025-01", revenue_usd=0.2),
            make_row(month="2025-02", revenue_usd=0.2)]
    result = group_sum(rows, "month")
    assert list(result) == ["2025-01", "2025-02"]
    assert result["2025-02"] == 0.3


def test_group_sum_other_value_column():
    rows = [make_row(category="a", profit_usd=5.0), make_row(category="a", profit_usd=2.5)]
    assert group_sum(rows, "category", "profit_usd") == {"a": 7.5}


def test_group_sum_empty():
    assert group_sum([], "month") == {}


def test_revenue_by_month():
    rows = [make_row(month="2025-01", revenue_usd=10.0), make_row(month="2025-01", revenue_usd=5.5),
            make_row(month="2025-02", revenue_usd=1.0)]
    assert revenue_by_month(rows) == {"2025-01": 15.5, "2025-02": 1.0}


def test_revenue_by_category():
    rows = [make_row(category="home", revenue_usd=10.0), make_row(category="toys", revenue_usd=3.0),
            make_row(category="home", revenue_usd=2.0)]
    assert revenue_by_category(rows) == {"home": 12.0, "toys": 3.0}


# ---- top_customers -------------------------------------------------------
def test_top_customers_orders_by_revenue_descending():
    rows = [make_row(customer_id="A", revenue_usd=5.0), make_row(customer_id="B", revenue_usd=50.0),
            make_row(customer_id="C", revenue_usd=20.0), make_row(customer_id="A", revenue_usd=40.0)]
    assert top_customers(rows, 3) == [("B", 50.0), ("A", 45.0), ("C", 20.0)]


def test_top_customers_limits_to_n():
    rows = [make_row(customer_id=c, revenue_usd=v) for c, v in (("A", 1.0), ("B", 2.0), ("C", 3.0))]
    assert top_customers(rows, 2) == [("C", 3.0), ("B", 2.0)]


def test_top_customers_tie_smaller_id_first():
    rows = [make_row(customer_id="B", revenue_usd=10.0), make_row(customer_id="A", revenue_usd=10.0),
            make_row(customer_id="C", revenue_usd=10.0)]
    assert top_customers(rows, 2) == [("A", 10.0), ("B", 10.0)]


def test_top_customers_n_larger_than_customers():
    assert top_customers([make_row(customer_id="A", revenue_usd=1.0)], 5) == [("A", 1.0)]


def test_top_customers_n_zero_and_empty():
    assert top_customers([make_row()], 0) == []
    assert top_customers([], 3) == []


# ---- average_order_value -------------------------------------------------
def test_average_order_value_only_completed_orders():
    rows = [make_row(revenue_usd=10.0), make_row(revenue_usd=20.0),
            make_row(status="refunded", revenue_usd=0.0), make_row(status="pending", revenue_usd=0.0)]
    assert average_order_value(rows) == 15.0


def test_average_order_value_rounds():
    rows = [make_row(revenue_usd=10.0), make_row(revenue_usd=10.0), make_row(revenue_usd=11.0)]
    assert average_order_value(rows) == 10.33


def test_average_order_value_none_when_nothing_completed():
    assert average_order_value([make_row(status="refunded", revenue_usd=0.0)]) is None
    assert average_order_value([]) is None


# ---- moving_average ------------------------------------------------------
def test_moving_average_window_three():
    assert moving_average([10, 20, 30, 40, 50], 3) == [10.0, 15.0, 20.0, 30.0, 40.0]


def test_moving_average_window_one_is_identity():
    assert moving_average([1, 2, 3], 1) == [1.0, 2.0, 3.0]


def test_moving_average_window_larger_than_data_is_running_mean():
    assert moving_average([2, 4, 6], 10) == [2.0, 3.0, 4.0]


def test_moving_average_rounds_to_two_decimals():
    assert moving_average([1, 1, 2], 3) == [1.0, 1.0, 1.33]


def test_moving_average_empty():
    assert moving_average([], 3) == []


@pytest.mark.parametrize("window", [0, -1])
def test_moving_average_bad_window(window):
    with pytest.raises(ValueError):
        moving_average([1, 2], window)


# ---- percentile ----------------------------------------------------------
def test_percentile_median_odd():
    assert percentile([3, 1, 2], 50) == 2


def test_percentile_median_even_interpolates():
    assert percentile([1, 2, 3, 4], 50) == 2.5


def test_percentile_extremes():
    data = [5, 1, 9, 3]
    assert percentile(data, 0) == 1
    assert percentile(data, 100) == 9


def test_percentile_quartiles():
    data = [10, 20, 30, 40, 50]
    assert percentile(data, 25) == 20
    assert percentile(data, 75) == 40


def test_percentile_interpolation():
    assert percentile([1, 2, 3, 4], 90) == pytest.approx(3.7)


def test_percentile_single_value():
    assert percentile([7], 30) == 7


def test_percentile_empty_is_none():
    assert percentile([], 50) is None


@pytest.mark.parametrize("p", [-1, 101])
def test_percentile_bad_p(p):
    with pytest.raises(ValueError):
        percentile([1, 2], p)


def test_percentile_does_not_modify_input():
    data = [3, 1, 2]
    percentile(data, 50)
    assert data == [3, 1, 2]


# ---- growth_rates --------------------------------------------------------
def test_growth_rates_basic():
    monthly = {"2025-01": 100.0, "2025-02": 150.0, "2025-03": 120.0}
    assert growth_rates(monthly) == {"2025-01": None, "2025-02": 50.0, "2025-03": -20.0}


def test_growth_rates_rounds_to_one_decimal():
    assert growth_rates({"2025-01": 3.0, "2025-02": 4.0})["2025-02"] == 33.3


def test_growth_rates_previous_zero_is_none():
    result = growth_rates({"2025-01": 0.0, "2025-02": 10.0, "2025-03": 20.0})
    assert result == {"2025-01": None, "2025-02": None, "2025-03": 100.0}


def test_growth_rates_uses_month_order_not_insertion_order():
    monthly = {"2025-02": 200.0, "2025-01": 100.0}
    assert growth_rates(monthly)["2025-02"] == 100.0


def test_growth_rates_empty():
    assert growth_rates({}) == {}


# ---- next_month ----------------------------------------------------------
@pytest.mark.parametrize("month,expected", [
    ("2024-01", "2024-02"), ("2024-09", "2024-10"), ("2024-11", "2024-12"),
    ("2024-12", "2025-01"), ("1999-12", "2000-01"),
])
def test_next_month(month, expected):
    assert next_month(month) == expected


# ---- monthly_retention ---------------------------------------------------
def orders_in(month, *customers, status="completed"):
    return [make_row(month=month, customer_id=c, status=status) for c in customers]


def test_monthly_retention_basic():
    rows = orders_in("2025-01", "A", "B", "C", "D") + orders_in("2025-02", "A", "B", "Z")
    result = monthly_retention(rows)
    assert result == {"2025-01": 0.5, "2025-02": None}


def test_monthly_retention_counts_customers_once():
    rows = orders_in("2025-01", "A", "A", "A", "B") + orders_in("2025-02", "A")
    assert monthly_retention(rows)["2025-01"] == 0.5


def test_monthly_retention_only_completed_orders_count():
    rows = (orders_in("2025-01", "A", "B") + orders_in("2025-02", "A")
            + orders_in("2025-02", "B", status="refunded"))
    assert monthly_retention(rows)["2025-01"] == 0.5


def test_monthly_retention_gap_month_is_zero():
    rows = orders_in("2025-01", "A") + orders_in("2025-03", "A")
    assert monthly_retention(rows) == {"2025-01": 0.0, "2025-03": None}


def test_monthly_retention_across_year_end():
    rows = orders_in("2024-12", "A", "B") + orders_in("2025-01", "A")
    assert monthly_retention(rows) == {"2024-12": 0.5, "2025-01": None}


def test_monthly_retention_rounds_to_two_decimals():
    rows = orders_in("2025-01", "A", "B", "C") + orders_in("2025-02", "A")
    assert monthly_retention(rows)["2025-01"] == 0.33


def test_monthly_retention_empty():
    assert monthly_retention([]) == {}
