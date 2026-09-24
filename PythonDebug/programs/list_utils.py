"""List utilities. None of these functions may change the list that is passed in.

find_max(numbers)
    The largest number, WITHOUT using the built-in max(). Works for negative numbers too.
    For an empty list it returns None.

remove_evens(numbers)
    A NEW list containing only the odd numbers, in the original order. The input list must be left
    unchanged.

chunk(items, size)
    Split a list into pieces of `size` items. The last piece may be shorter.
    chunk([1, 2, 3, 4, 5], 2) is [[1, 2], [3, 4], [5]]. A size of 0 or less raises ValueError.

remove_duplicates(items)
    A new list without repeated items. The first occurrence of each item is kept, in order.

flatten(nested)
    Flatten ONE level: [[1, 2], [3], []] -> [1, 2, 3].
"""


def find_max(numbers):
    if not numbers:
        return None
    largest = float("-inf")
    for number in numbers:
        if number > largest:
            largest = number
    return largest


def remove_evens(numbers):
    return [n for n in numbers if n % 2 != 0]


def chunk(items, size):
    if size <= 0:
        raise ValueError("size must be positive")
    return [items[start:start + size] for start in range(0, len(items), size)]


def remove_duplicates(items):
    return list(dict.fromkeys(items))


def flatten(nested):
    flat = []
    for inner in nested:
        flat.extend(inner)
    return flat


def main():
    print("max:", find_max([-8, -3, -20]))
    print("odds:", remove_evens([1, 2, 3, 4, 5, 6]))
    print("chunks:", chunk([1, 2, 3, 4, 5], 2))
    print("unique:", remove_duplicates([3, 1, 3, 2, 1]))
    print("flat:", flatten([[1, 2], [3], []]))


if __name__ == "__main__":
    main()
