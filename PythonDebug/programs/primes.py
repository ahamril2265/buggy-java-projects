"""Prime numbers.

is_prime(n)
    True if n is a prime number (only divisible by 1 and itself). Numbers below 2 are not prime.

primes_up_to(n)
    A list of all prime numbers from 2 up to and INCLUDING n, in increasing order.

prime_factors(n)
    The prime factors of n in increasing order, repeated as often as they divide n.
    prime_factors(12) is [2, 2, 3]. For n below 2 it returns an empty list.
"""


def is_prime(n):
    if n < 2:
        return False
    for divisor in range(2, int(n ** 0.5)):
        if n % divisor == 0:
            return False
    return True


def primes_up_to(n):
    return [number for number in range(2, n) if is_prime(number)]


def prime_factors(n):
    factors = []
    divisor = 2
    while n >= 2:
        if n % divisor == 0:
            factors.append(divisor)
            n //= divisor
        else:
            divisor += 1
    return factors


def main():
    print("Primes up to 30:", primes_up_to(30))
    print("Is 49 prime?", is_prime(49))
    print("Factors of 360:", prime_factors(360))


if __name__ == "__main__":
    main()
