"""The orchestrator. Runs every stage in order and reports what happened.

run_pipeline(orders_path, customers_path, products_path, conn, rates=FX_RATES_TO_USD,
             rejects_path=None)

  1. ingest     load_orders (valid + rejected), then dedupe_orders
  2. clean      clean_customers / clean_products
  3. transform  enrich_orders (join, currency, coupons, first-order flag)
  4. incremental filter: keep only rows whose order_date is ON OR AFTER the stored watermark
                (the watermark day itself is loaded again on purpose, so late-arriving orders for
                that day are not lost - the upsert makes this safe). No watermark = keep everything.
  5. load       upsert the kept rows, then move the watermark forward to the latest order_date
  6. reject log if rejects_path is given, write the rejected rows there (report.write_rejects)

Returns a summary dict:
    read                valid orders parsed from the file (before de-duplication)
    rejected            rows that failed validation
    duplicates_removed  valid orders dropped by dedupe_orders
    orphans             orders skipped because their product is unknown
    loaded              rows handed to the database this run (after the incremental filter)
    inserted, updated   as returned by store.upsert_orders
    watermark           the watermark after the run, as "YYYY-MM-DD" text, or None
"""
from pipeline.clean import clean_customers, clean_products
from pipeline.config import FX_RATES_TO_USD
from pipeline.ingest import dedupe_orders, load_orders, read_csv
from pipeline.report import write_rejects
from pipeline.store import get_watermark, init_db, set_watermark, upsert_orders
from pipeline.transform import enrich_orders


def run_pipeline(orders_path, customers_path, products_path, conn,
                 rates=FX_RATES_TO_USD, rejects_path=None):
    init_db(conn)

    valid_orders, rejected = load_orders(orders_path)
    orders = dedupe_orders(valid_orders)
    customers = clean_customers(read_csv(customers_path))
    products = clean_products(read_csv(products_path))

    rows, orphans = enrich_orders(orders, customers, products, rates)

    watermark = get_watermark(conn)
    fresh = [row for row in rows if watermark is None or row["order_date"] >= watermark]

    inserted, updated = upsert_orders(conn, fresh)
    if fresh:
        set_watermark(conn, max(row["order_date"] for row in fresh))

    if rejects_path:
        write_rejects(rejects_path, rejected)

    final = get_watermark(conn)
    return {
        "read": len(valid_orders),
        "rejected": len(rejected),
        "duplicates_removed": len(valid_orders) - len(orders),
        "orphans": len(orphans),
        "loaded": len(fresh),
        "inserted": inserted,
        "updated": updated,
        "watermark": final.isoformat() if final else None,
    }
