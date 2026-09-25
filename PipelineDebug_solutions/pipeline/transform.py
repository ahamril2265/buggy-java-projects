"""Stage 3 - transformation. Join the clean orders with customers and products.

line_total(order)
    What the customer paid for the order line, in the ORDER'S OWN currency (not yet converted):
      quantity * unit_price, then the coupon (config.COUPONS) is applied to that whole line total:
        percent coupon  takes that percentage off the line total
        flat coupon     takes that amount off the line total
        unknown/empty coupon  no discount
      The result is never below 0, and is rounded to 4 decimals.

enrich_orders(orders, customers, products, rates=FX_RATES_TO_USD)
    Returns (rows, orphans).
    Each row describes one order:
      order_id, customer_id, country, category, order_date (date), month ("YYYY-MM"), quantity,
      status, revenue_usd, cost_usd, profit_usd, is_first_order
    Rules:
      - country comes from the customer; an order whose customer is unknown gets "UNKNOWN"
      - category comes from the product; an order whose product is unknown is NOT included in rows
        and its order_id goes into the orphans list instead
      - only orders with status "completed" earn money. For any other status revenue_usd, cost_usd
        and profit_usd are all 0.0
      - revenue_usd = line_total converted to USD (clean.to_usd)
        cost_usd    = quantity * product cost (already USD), rounded to 2 decimals
        profit_usd  = revenue_usd - cost_usd, rounded to 2 decimals
      - is_first_order is True for exactly one order per customer: the customer's EARLIEST order
        by (order_date, order_id), whatever its status. Every other order is False.
      - rows keep the order of the input list

mark_first_orders(rows)
    Sets the is_first_order flag on every row as described above (modifies the rows in place).
"""
from pipeline.clean import to_usd
from pipeline.config import COUPONS, FX_RATES_TO_USD


def line_total(order):
    gross = order["quantity"] * order["unit_price"]
    kind, value = COUPONS.get(order["coupon"], (None, 0))
    if kind == "percent":
        gross = gross * (1 - value / 100)
    elif kind == "flat":
        gross = gross - value
    return round(max(gross, 0.0), 4)


def mark_first_orders(rows):
    first_order_of = {}
    for row in sorted(rows, key=lambda r: (r["order_date"], r["order_id"])):
        first_order_of.setdefault(row["customer_id"], row["order_id"])
    for row in rows:
        row["is_first_order"] = first_order_of[row["customer_id"]] == row["order_id"]


def enrich_orders(orders, customers, products, rates=FX_RATES_TO_USD):
    country_of = {c["customer_id"]: c["country"] for c in customers}
    product_of = {p["product_id"]: p for p in products}

    rows, orphans = [], []
    for order in orders:
        product = product_of.get(order["product_id"])
        if product is None:
            orphans.append(order["order_id"])
            continue

        if order["status"] == "completed":
            revenue = to_usd(line_total(order), order["currency"], rates)
            cost = round(order["quantity"] * product["cost"], 2)
        else:
            revenue = 0.0
            cost = 0.0

        rows.append(
            {
                "order_id": order["order_id"],
                "customer_id": order["customer_id"],
                "country": country_of.get(order["customer_id"], "UNKNOWN"),
                "category": product["category"],
                "order_date": order["order_date"],
                "month": order["order_date"].strftime("%Y-%m"),
                "quantity": order["quantity"],
                "status": order["status"],
                "revenue_usd": revenue,
                "cost_usd": cost,
                "profit_usd": round(revenue - cost, 2),
            }
        )

    mark_first_orders(rows)
    return rows, orphans
