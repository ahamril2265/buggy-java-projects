"""Temperature tools.

celsius_to_fahrenheit(c)  ->  c * 9/5 + 32
fahrenheit_to_celsius(f)  ->  (f - 32) * 5/9

describe_temperature(c) returns one of these words:
  "freezing"  0 degrees or below
  "cold"      above 0 and below 10
  "mild"      10 or above and below 20
  "warm"      20 or above and below 30
  "hot"       30 or above

average_temperature(readings) returns the average of a list of readings, rounded to 1 decimal
place. For an empty list it returns None.
"""


def celsius_to_fahrenheit(celsius):
    return celsius * 9 / 5 + 32


def fahrenheit_to_celsius(fahrenheit):
    return (fahrenheit - 32) * 5 / 9


def describe_temperature(celsius):
    if celsius <= 0:
        return "freezing"
    if celsius < 10:
        return "cold"
    if celsius < 20:
        return "mild"
    if celsius < 30:
        return "warm"
    return "hot"


def average_temperature(readings):
    if not readings:
        return None
    return round(sum(readings) / len(readings), 1)


def main():
    for celsius in (-40, 0, 5, 10, 25, 37, 100):
        print(f"{celsius} C = {celsius_to_fahrenheit(celsius)} F  ({describe_temperature(celsius)})")
    print("212 F =", fahrenheit_to_celsius(212), "C")
    print("Average:", average_temperature([10, 15, 20.5]))


if __name__ == "__main__":
    main()
