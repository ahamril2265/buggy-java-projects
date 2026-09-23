"""A tiny calculator.

calculate(a, op, b)
    Supports the operators "+", "-", "*" and "/". Dividing by zero raises
    ValueError("Cannot divide by zero"). Any other operator raises ValueError.
    "/" always gives a float, for example calculate(6, "/", 3) is 2.0.

parse_and_calculate(expression)
    Evaluates text such as "3 + 4" or "2.5 * -2". The expression is exactly three parts separated
    by spaces: number, operator, number. Whole numbers stay whole numbers (int), numbers with a
    decimal point are floats. Anything else raises ValueError.
"""


def calculate(a, op, b):
    if op == "+":
        return a + b
    if op == "-":
        return a - b
    if op == "*":
        return a * b
    if op == "/":
        if b == 0:
            raise ValueError("Cannot divide by zero")
        return a / b
    raise ValueError(f"Unknown operator: {op}")


def _to_number(text):
    try:
        return int(text)
    except ValueError:
        return float(text)


def parse_and_calculate(expression):
    parts = expression.split()
    if len(parts) != 3:
        raise ValueError("Expected: number operator number")
    left, op, right = parts
    return calculate(_to_number(left), op, _to_number(right))


def main():
    for expression in ("3 + 4", "10 - 4", "6 / 3", "2.5 * 2", "7 / 0"):
        try:
            print(expression, "=", parse_and_calculate(expression))
        except ValueError as error:
            print(expression, "->", error)


if __name__ == "__main__":
    main()
