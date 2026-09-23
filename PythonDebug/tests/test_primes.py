import pytest

from programs.primes import is_prime, prime_factors, primes_up_to


@pytest.mark.parametrize("n", [2, 3, 5, 7, 11, 13, 97])
def test_primes_are_prime(n):
    assert is_prime(n)


@pytest.mark.parametrize("n", [4, 6, 8, 9, 15, 25, 49, 100])
def test_composites_are_not_prime(n):
    assert not is_prime(n)


@pytest.mark.parametrize("n", [-7, -1, 0, 1])
def test_numbers_below_two_are_not_prime(n):
    assert not is_prime(n)


def test_primes_up_to_thirty():
    assert primes_up_to(30) == [2, 3, 5, 7, 11, 13, 17, 19, 23, 29]


def test_primes_up_to_includes_n_when_n_is_prime():
    assert primes_up_to(13)[-1] == 13
    assert primes_up_to(2) == [2]


def test_primes_up_to_a_small_number_is_empty():
    assert primes_up_to(1) == []


def test_prime_factors():
    assert prime_factors(12) == [2, 2, 3]
    assert prime_factors(360) == [2, 2, 2, 3, 3, 5]
    assert prime_factors(13) == [13]


def test_prime_factors_of_numbers_below_two():
    assert prime_factors(1) == []
    assert prime_factors(0) == []
