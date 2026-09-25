"""Helpers that build small, valid test objects. Not a test file."""
from datetime import date


def make_order(**overrides):
    order = {
        "order_id": "O1",
        "customer_id": "C1",
        "product_id": "P1",
        "quantity": 1,
        "unit_price": 10.0,
        "currency": "USD",
        "order_date": date(2025, 1, 15),
        "status": "completed",
        "coupon": "",
    }
    order.update(overrides)
    return order


def make_row(**overrides):
    row = {
        "order_id": "O1",
        "customer_id": "C1",
        "country": "US",
        "category": "home",
        "order_date": date(2025, 1, 15),
        "month": "2025-01",
        "quantity": 1,
        "status": "completed",
        "revenue_usd": 10.0,
        "cost_usd": 4.0,
        "profit_usd": 6.0,
        "is_first_order": True,
    }
    row.update(overrides)
    return row


def make_customer(**overrides):
    customer = {
        "customer_id": "C1",
        "name": "Test",
        "email": "t@example.com",
        "country": "US",
        "signup_date": date(2024, 1, 1),
    }
    customer.update(overrides)
    return customer


def make_product(**overrides):
    product = {"product_id": "P1", "name": "Thing", "category": "home", "cost": 4.0}
    product.update(overrides)
    return product


ORDER_HEADER = "order_id,customer_id,product_id,quantity,unit_price,currency,order_date,status,coupon"


def write_csv(path, header, lines):
    path.write_text(header + "\n" + "\n".join(lines) + "\n", encoding="utf-8")
    return path
