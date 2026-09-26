"""Stage 1 - ingestion. Read raw CSV text and turn it into validated records.

read_csv(path)
    Returns a list of dicts, one per data row. Header names and cell values are stripped of
    surrounding whitespace. Empty cells are the empty string "".

parse_date(text)
    Accepts "YYYY-MM-DD" (2025-01-05) and European "DD/MM/YYYY" (05/01/2025, which is 5 January).
    Returns a datetime.date. Anything else raises ValueError.

parse_order_row(row)
    Validate one raw order row and return a clean order dict with these keys:
      order_id (stripped, upper-case)   customer_id (upper-case)   product_id (upper-case)
      quantity (int, must be 1 or more) unit_price (float, 0 or more)
      currency (upper-case, must be a currency in config.FX_RATES_TO_USD)
      order_date (datetime.date)        status (via clean.normalize_status)
      coupon (upper-case, or "" when empty)
    Any problem raises ValueError whose message says what is wrong.

load_orders(path)
    Read orders.csv and parse every row. Returns (orders, rejected):
      orders    the valid, parsed order dicts in file order
      rejected  one dict {"line": n, "reason": text} per bad row, where n is the row's LINE NUMBER
                IN THE FILE (the header is line 1, so the first data row is line 2)

dedupe_orders(orders)
    The same order can appear several times (updates arrive as new rows). Keep only the LAST
    occurrence of every order_id. Returns the survivors sorted by order_id.
"""
import csv
import math
from datetime import datetime

from pipeline.clean import normalize_status
from pipeline.config import FX_RATES_TO_USD


def read_csv(path):
    with open(path, newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        return [
            {key.strip(): (value or "").strip() for key, value in row.items()}
            for row in reader
        ]


def parse_date(text):
    text = text.strip()
    for fmt in ("%Y-%m-%d", "%d/%m/%Y"):
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            continue
    raise ValueError(f"unrecognised date: {text!r}")


def parse_order_row(row):
    text = {}
    for field in ("order_id", "customer_id", "product_id", "currency", "coupon"):
        raw = row.get(field)
        if raw is None:  
            raw = ""
        if not isinstance(raw, str):  
            raise ValueError(f"{field} must be text, got {raw!r}")
        text[field] = raw.strip().upper()

    for field in ("order_id", "customer_id", "currency"):
        if not text[field]:
            raise ValueError(f"missing {field}")

    if text["currency"] not in FX_RATES_TO_USD:
        raise ValueError(f"unknown currency: {text['currency']!r}")

    raw_quantity = row.get("quantity")
    if isinstance(raw_quantity, bool) or (
        isinstance(raw_quantity, float) and not raw_quantity.is_integer()
    ):
        raise ValueError(f"quantity must be a whole number, got {raw_quantity!r}")
    try:
        quantity = int(raw_quantity)  
    except (TypeError, ValueError) as e:  
        raise ValueError(f"quantity must be a whole number, got {raw_quantity!r}") from e
    if quantity < 1:
        raise ValueError(f"quantity must be at least 1, got {quantity}")

    raw_price = row.get("unit_price")
    if isinstance(raw_price, bool):
        raise ValueError(f"unit_price must be a number, got {raw_price!r}")
    try:
        unit_price = float(raw_price)
    except (TypeError, ValueError) as e:
        raise ValueError(f"unit_price must be a number, got {raw_price!r}") from e
    if not math.isfinite(unit_price):
        raise ValueError(f"unit_price must be finite, got {raw_price!r}")
    if unit_price < 0:
        raise ValueError(f"unit_price cannot be negative, got {unit_price}")

    raw_date = row.get("order_date")
    if not isinstance(raw_date, str):  
        raise ValueError(f"order_date must be text, got {raw_date!r}")

    return {
        "order_id": text["order_id"],
        "customer_id": text["customer_id"],
        "product_id": text["product_id"],
        "quantity": quantity,
        "unit_price": unit_price,
        "currency": text["currency"],
        "order_date": parse_date(raw_date),
        "status": normalize_status(row.get("status") or ""),
        "coupon": text["coupon"],
    }

def load_orders(path):
    orders, rejected = [], []
    for line, row in enumerate(read_csv(path), start=2):
        try:
            orders.append(parse_order_row(row))
        except ValueError as error:
            rejected.append({"line": line, "reason": str(error), "row": row})
    return orders, rejected


def dedupe_orders(orders):
    latest = {}
    for order in orders:
        if order["order_id"] not in latest:
            latest[order["order_id"]] = order
        else:
            latest[order["order_id"]] = order
    return [latest[key] for key in sorted(latest)]
