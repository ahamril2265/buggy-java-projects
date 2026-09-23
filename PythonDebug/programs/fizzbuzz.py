"""FizzBuzz.

fizzbuzz(n) returns a list with one entry for each number from 1 up to and INCLUDING n:
  - "FizzBuzz" if the number is divisible by both 3 and 5
  - "Fizz"     if the number is divisible by 3
  - "Buzz"     if the number is divisible by 5
  - otherwise the number itself as a string, for example "7"

If n is 0 or negative, the list is empty.
"""


def fizzbuzz(n):
    results = []
    for number in range(1, n):
        if number % 3 == 0:
            results.append("Fizz")
        elif number % 5 == 0:
            results.append("Buzz")
        elif number % 15 == 0:
            results.append("FizzBuzz")
        else:
            results.append(str(number))
    return results


def main():
    for line in fizzbuzz(15):
        print(line)


if __name__ == "__main__":
    main()
