"""Stage 2 - cleaning. Small, pure functions that make messy values consistent.

normalize_email(email)      Strip surrounding whitespace and lower-case it.
normalize_country(country)  Strip whitespace, upper-case it, then map known aliases to the 2-letter
                            code (see config.COUNTRY_ALIASES): " usa " -> "US", "uk" -> "GB".
                            Anything else is just the stripped, upper-cased text ("de" -> "DE").
normalize_status(status)    Strip + lower-case. "canceled" (US spelling) means "cancelled".
                            A status that is not in config.VALID_STATUSES raises ValueError.
to_usd(amount, currency)    Convert to US dollars and round to 2 decimals, HALF UP (2.675 -> 2.68,
                            not the 2.67 that Python's round() gives). An unknown currency raises
                            ValueError.
clean_customers(rows)       rows are dicts read from customers.csv (customer_id, name, email,
                            country, signup_date). Returns a list of clean customer dicts, sorted by
                            customer_id:
                              - customer_id is stripped and upper-cased; rows without one are dropped
                              - email and country are normalised
                              - signup_date is converted to a datetime.date
                              - if a customer_id appears more than once, the FIRST row wins
clean_products(rows)        rows from products.csv (product_id, name, category, cost). Returns dicts
                            sorted by product_id: product_id upper-cased, category stripped and
                            lower-cased, cost converted to float. Rows without a product_id are
                            dropped; duplicates: the FIRST row wins.
"""
from decimal import Decimal, ROUND_HALF_UP

from pipeline.config import COUNTRY_ALIASES, FX_RATES_TO_USD, VALID_STATUSES


def normalize_email(email):
    return email.strip().lower()


def normalize_country(country):
    key = country.strip().upper()
    return COUNTRY_ALIASES.get(key, key)


def normalize_status(status):
    value = status.strip().lower()
    if value == "canceled":
        value = "cancelled"
    if value not in VALID_STATUSES:
        raise ValueError(f"unknown status: {status!r}")
    return value


def to_usd(amount, currency, rates=FX_RATES_TO_USD):
    if currency not in rates:
        raise ValueError(f"unknown currency: {currency!r}")
    usd = Decimal(str(amount)) * Decimal(str(rates[currency]))
    return float(usd.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def clean_customers(rows):
    from pipeline.ingest import parse_date

    seen = {}
    for row in rows:
        customer_id = row.get("customer_id", "").strip().upper()
        if not customer_id or customer_id in seen:
            continue
        seen[customer_id] = {
            "customer_id": customer_id,
            "name": row.get("name", "").strip(),
            "email": normalize_email(row.get("email", "")),
            "country": normalize_country(row.get("country", "")),
            "signup_date": parse_date(row["signup_date"]),
        }
    return [seen[key] for key in sorted(seen)]


def clean_products(rows):
    seen = {}
    for row in rows:
        product_id = row.get("product_id", "").strip().upper()
        if not product_id or product_id in seen:
            continue
        seen[product_id] = {
            "product_id": product_id,
            "name": row.get("name", "").strip(),
            "category": row.get("category", "").strip().lower(),
            "cost": float(row["cost"]),
        }
    return [seen[key] for key in sorted(seen)]
