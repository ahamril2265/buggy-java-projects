"""Stage 4 - aggregation. Turn enriched rows into the numbers the business asks for.

Every function receives the `rows` produced by transform.enrich_orders.

group_sum(rows, key, value="revenue_usd")
    Sum `value` for each distinct row[key]. Returns a dict sorted by key, sums rounded to 2 decimals.

revenue_by_month(rows)      group_sum by "month".
revenue_by_category(rows)   group_sum by "category".

top_customers(rows, n)
    The n customers with the highest total revenue_usd, as [(customer_id, revenue), ...], best
    first. Equal revenue: the smaller customer_id comes first. Revenue rounded to 2 decimals.

average_order_value(rows)
    Average revenue_usd over the COMPLETED orders only, rounded to 2 decimals; None if there are
    no completed orders.

moving_average(values, window)
    For every position, the mean of the last `window` values ending at that position (the position
    itself included). At the start, where fewer than `window` values exist, use the ones that do
    exist. Each mean is rounded to 2 decimals. window below 1 raises ValueError.

percentile(values, p)
    The p-th percentile (0-100) using linear interpolation between the closest ranks (the numpy
    default): sort the values, rank = (len - 1) * p / 100, interpolate between the values on either
    side of the rank. Returns None for an empty list. p outside 0-100 raises ValueError.

growth_rates(monthly)
    monthly is a dict month -> revenue. Returns a dict month -> growth in percent versus the
    PREVIOUS month (in month order), rounded to 1 decimal. The first month, and any month whose
    previous month had revenue 0, gets None.

next_month(month)
    "2024-11" -> "2024-12", "2024-12" -> "2025-01".

monthly_retention(rows)
    For every month that has completed orders: the fraction of that month's customers (customers
    with at least one completed order in it) who ALSO have a completed order in the next calendar
    month, rounded to 2 decimals. A next month with no orders gives 0.0. The last month in the data
    has no "next" data yet, so its value is None. Returns a dict sorted by month.
"""
import math
from collections import defaultdict


def group_sum(rows, key, value="revenue_usd"):
    totals = defaultdict(float)
    for row in rows:
        totals[row[key]] += row[value]
    return {k: round(totals[k], 2) for k in sorted(totals)}


def revenue_by_month(rows):
    return group_sum(rows, "month")


def revenue_by_category(rows):
    return group_sum(rows, "category")


def top_customers(rows, n):
    totals = defaultdict(float)
    for row in rows:
        totals[row["customer_id"]] += row["revenue_usd"]
    ranked = sorted(totals.items(), key=lambda item: -item[1])
    return [(customer, round(revenue, 2)) for customer, revenue in ranked[:n]]


def average_order_value(rows):
    amounts = [row["revenue_usd"] for row in rows]
    if not amounts:
        return None
    return round(sum(amounts) / len(amounts), 2)


def moving_average(values, window):
    if window < 1:
        raise ValueError("window must be at least 1")
    result = []
    for index in range(len(values)):
        chunk = values[max(0, index - window): index + 1]
        result.append(round(sum(chunk) / len(chunk), 2))
    return result


def percentile(values, p):
    if not 0 <= p <= 100:
        raise ValueError("p must be between 0 and 100")
    if not values:
        return None
    ordered = sorted(values)
    rank = (len(ordered) - 1) * p / 100
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return float(ordered[low])
    return ordered[low] + (ordered[high] - ordered[low]) * (high - rank)


def growth_rates(monthly):
    result = {}
    previous = None
    for month in sorted(monthly):
        current = monthly[month]
        if previous is None or previous == 0:
            result[month] = None
        else:
            result[month] = round((current - previous) / current * 100, 1)
        previous = current
    return result


def next_month(month):
    year, number = (int(part) for part in month.split("-"))
    if number == 12:
        return f"{year}-01"
    return f"{year}-{number + 1:02d}"


def monthly_retention(rows):
    customers_by_month = defaultdict(set)
    for row in rows:
        if row["status"] == "completed":
            customers_by_month[row["month"]].add(row["customer_id"])

    months = sorted(customers_by_month)
    result = {}
    for month in months:
        if month == months[-1]:
            result[month] = None
            continue
        current = customers_by_month[month]
        following = customers_by_month.get(next_month(month), set())
        result[month] = round(len(current & following) / len(current), 2)
    return result
