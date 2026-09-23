import pytest

from programs.temperature import (average_temperature, celsius_to_fahrenheit, describe_temperature,
                                  fahrenheit_to_celsius)


def test_celsius_to_fahrenheit():
    assert celsius_to_fahrenheit(0) == 32
    assert celsius_to_fahrenheit(100) == 212
    assert celsius_to_fahrenheit(37) == pytest.approx(98.6)


def test_fahrenheit_to_celsius():
    assert fahrenheit_to_celsius(32) == 0
    assert fahrenheit_to_celsius(212) == 100
    assert fahrenheit_to_celsius(50) == pytest.approx(10)


def test_minus_forty_is_the_same_in_both_scales():
    assert celsius_to_fahrenheit(-40) == -40
    assert fahrenheit_to_celsius(-40) == pytest.approx(-40)


def test_zero_degrees_is_freezing():
    assert describe_temperature(0) == "freezing"
    assert describe_temperature(-12) == "freezing"


@pytest.mark.parametrize("celsius, word", [
    (0.1, "cold"), (5, "cold"), (9.9, "cold"),
    (10, "mild"), (19.9, "mild"),
    (20, "warm"), (29.9, "warm"),
    (30, "hot"), (45, "hot"),
])
def test_describe_temperature_ranges(celsius, word):
    assert describe_temperature(celsius) == word


def test_average_temperature_is_rounded_to_one_decimal():
    assert average_temperature([20, 22, 24]) == 22.0
    assert average_temperature([10, 15, 20.5]) == 15.2


def test_average_of_no_readings_is_none():
    assert average_temperature([]) is None
