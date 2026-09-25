"""Stage 5 - storage. Load the enriched rows into SQLite and query them back.

Tables: fact_orders (one row per order_id, the primary key) and meta (key/value, holds the
watermark).

init_db(conn)
    Create the tables if they do not exist. Safe to call any number of times.

upsert_orders(conn, rows)
    Insert every row; if its order_id is already stored, UPDATE that row instead (never a
    duplicate). Returns (inserted, updated): how many rows were new, and how many replaced an
    existing order_id. Running the same rows twice gives (n, 0) and then (0, n).
    order_date is stored as "YYYY-MM-DD" text, is_first_order as 1/0.

get_watermark(conn) / set_watermark(conn, day)
    The watermark is the latest order_date that has been loaded, so the next run can skip old data.
    get_watermark returns a datetime.date, or None if nothing was ever loaded.
    set_watermark only ever moves FORWARD: an older date than the stored one is ignored.

sql_revenue_by_month(conn)
    {month: revenue} summed in SQL, rounded to 2 decimals, sorted by month.

sql_top_countries(conn, n)
    [(country, revenue), ...] the n countries with the most revenue, best first; equal revenue:
    alphabetical by country. Revenue rounded to 2 decimals.

sql_repeat_customers(conn)
    How many customers have placed TWO OR MORE completed orders.
"""
from datetime import date

SCHEMA = """
CREATE TABLE IF NOT EXISTS fact_orders (
    order_id       TEXT PRIMARY KEY,
    customer_id    TEXT,
    country        TEXT,
    category       TEXT,
    order_date     TEXT,
    month          TEXT,
    quantity       INTEGER,
    status         TEXT,
    revenue_usd    REAL,
    cost_usd       REAL,
    profit_usd     REAL,
    is_first_order INTEGER
);
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT
);
"""

UPSERT = """
INSERT INTO fact_orders (order_id, customer_id, country, category, order_date, month, quantity,
                         status, revenue_usd, cost_usd, profit_usd, is_first_order)
VALUES (:order_id, :customer_id, :country, :category, :order_date, :month, :quantity,
        :status, :revenue_usd, :cost_usd, :profit_usd, :is_first_order)
ON CONFLICT(order_id) DO UPDATE SET
    customer_id    = excluded.customer_id,
    country        = excluded.country,
    category       = excluded.category,
    order_date     = excluded.order_date,
    month          = excluded.month,
    quantity       = excluded.quantity,
    status         = excluded.status,
    revenue_usd    = excluded.revenue_usd,
    cost_usd       = excluded.cost_usd,
    profit_usd     = excluded.profit_usd,
    is_first_order = excluded.is_first_order
"""


def init_db(conn):
    conn.executescript(SCHEMA)
    conn.commit()


def upsert_orders(conn, rows):
    inserted = updated = 0
    for row in rows:
        params = dict(row)
        params["order_date"] = row["order_date"].isoformat()
        params["is_first_order"] = int(row["is_first_order"])
        conn.execute(UPSERT, params)
        existed = conn.execute(
            "SELECT 1 FROM fact_orders WHERE order_id = ?", (row["order_id"],)
        ).fetchone()
        if existed:
            updated += 1
        else:
            inserted += 1
    conn.commit()
    return inserted, updated


def get_watermark(conn):
    row = conn.execute("SELECT value FROM meta WHERE key = 'watermark'").fetchone()
    return date.fromisoformat(row[0]) if row else None


def set_watermark(conn, day):
    conn.execute(
        "INSERT INTO meta (key, value) VALUES ('watermark', ?) "
        "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        (day.isoformat(),),
    )
    conn.commit()


def sql_revenue_by_month(conn):
    cursor = conn.execute(
        "SELECT month, ROUND(SUM(revenue_usd), 2) FROM fact_orders "
        "GROUP BY month ORDER BY month"
    )
    return {month: revenue for month, revenue in cursor}


def sql_top_countries(conn, n):
    cursor = conn.execute(
        "SELECT country, ROUND(SUM(revenue_usd), 2) AS revenue FROM fact_orders "
        "GROUP BY country ORDER BY revenue DESC, country DESC LIMIT ?",
        (n,),
    )
    return [(country, revenue) for country, revenue in cursor]


def sql_repeat_customers(conn):
    row = conn.execute(
        "SELECT COUNT(*) FROM ("
        "  SELECT customer_id FROM fact_orders WHERE status = 'completed' "
        "  GROUP BY customer_id HAVING COUNT(*) > 2)"
    ).fetchone()
    return row[0]
