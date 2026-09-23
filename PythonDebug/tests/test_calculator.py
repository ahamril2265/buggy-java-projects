import pytest

from programs.calculator import calculate, parse_and_calculate


def test_addition_and_multiplication():
    assert calculate(2, "+", 3) == 5
    assert calculate(4, "*", 2.5) == 10


def test_subtraction_keeps_the_order_of_the_numbers():
    assert calculate(10, "-", 4) == 6
    assert calculate(4, "-", 10) == -6


def test_division_gives_a_float():
    assert calculate(7, "/", 2) == 3.5
    assert calculate(6, "/", 3) == 2.0


def test_division_by_zero_is_an_error():
    with pytest.raises(ValueError, match="Cannot divide by zero"):
        calculate(5, "/", 0)


def test_unknown_operator_is_an_error():
    with pytest.raises(ValueError):
        calculate(1, "%", 2)


def test_parse_whole_numbers():
    assert parse_and_calculate("3 + 4") == 7
    assert parse_and_calculate("10 - 4") == 6
    assert parse_and_calculate("-3 * 2") == -6


def test_parse_keeps_whole_numbers_as_int():
    assert isinstance(parse_and_calculate("3 + 4"), int)


def test_parse_decimal_numbers():
    assert parse_and_calculate("2.5 * 2") == 5.0
    assert parse_and_calculate("1.5 + 1.5") == 3.0
    assert parse_and_calculate("10 / 4") == 2.5


@pytest.mark.parametrize("bad", ["", "3 +", "3 + 4 + 5", "three + 4"])
def test_parse_rejects_malformed_expressions(bad):
    with pytest.raises(ValueError):
        parse_and_calculate(bad)
