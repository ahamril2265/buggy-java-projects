"""Pipeline configuration: the business rules every stage shares.

FX_RATES_TO_USD   multiply an amount in the given currency by the rate to get US dollars.
VALID_STATUSES    the only order statuses the pipeline accepts (after normalising).
COUNTRY_ALIASES   alternative spellings of a country (upper case) -> its 2-letter code.
COUPONS           coupon code -> (kind, value).
                    ("percent", 10)  takes 10% off the whole order line
                    ("flat", 5)      takes 5 off the order line, in the ORDER's own currency
"""

FX_RATES_TO_USD = {"USD": 1.0, "EUR": 1.10, "GBP": 1.25}

VALID_STATUSES = ("completed", "refunded", "cancelled", "pending")

COUNTRY_ALIASES = {
    "USA": "US",
    "UNITED STATES": "US",
    "UK": "GB",
    "UNITED KINGDOM": "GB",
}

COUPONS = {
    "SAVE10": ("percent", 10),
    "SAVE25": ("percent", 25),
    "FLAT5": ("flat", 5),
}
