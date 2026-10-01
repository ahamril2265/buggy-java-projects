"""Stage 6 - reporting. Turn numbers into text people (and other tools) can read.

format_money(amount)
    "$1,234.50" - dollar sign, thousands separators, always 2 decimals.
    A negative amount puts the minus sign BEFORE the dollar sign: "-$5.00".

format_percent(value)
    "+5.0%" / "-2.5%" / "+0.0%" (always signed, 1 decimal). None gives "n/a".

rows_to_csv(rows, columns)
    CSV text: a header line with `columns`, then one line per row (a dict), values in the order of
    `columns`. Lines end with "\\n". Values containing commas or quotes are quoted properly.

render_monthly_report(monthly, growth)
    One line per month, in month order:  "2024-11  $1,234.50  +5.0%"
    (month, two spaces, money, two spaces, growth via format_percent). Lines joined with "\\n".

render_summary(summary)
    The run summary (see runner.run_pipeline) as text, one "label: value" line per item, in this
    order: read, rejected, duplicates_removed, orphans, loaded, inserted, updated, watermark.
    A watermark of None prints as "none".

write_rejects(path, rejected)
    Write the rejected rows as a CSV file with the columns line,reason.
"""
import csv


def format_money(amount):
    sign = "-" if amount < 0 else ""
    return f"{sign}${abs(amount):,.2f}"


def format_percent(value):
    if value is None:
        return "n/a"
    return f"{value:+.1f}%"


def rows_to_csv(rows, columns):
    import io

    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(columns)
    for row in rows:
        writer.writerow([row[column] for column in columns])
    return buffer.getvalue()


def render_monthly_report(monthly, growth):
    lines = []
    for month in sorted(monthly):
        lines.append(f"{month}  {format_money(monthly[month])}  {format_percent(growth.get(month))}")
    return "\n".join(lines)


SUMMARY_ORDER = ("read", "rejected", "duplicates_removed", "orphans", "loaded", "inserted",
                 "updated", "watermark")


def render_summary(summary):
    lines = []
    for key in SUMMARY_ORDER:
        value = summary[key]
        if key == "watermark" and value is None:
            value = "none"
        lines.append(f"{key}: {value}")
    return "\n".join(lines)


def write_rejects(path, rejected):
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["line", "reason"])
        for item in rejected:
            writer.writerow([item["line"], item["reason"]])
