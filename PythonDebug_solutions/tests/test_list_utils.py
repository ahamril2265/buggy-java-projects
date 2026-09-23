import pytest

from programs.list_utils import chunk, find_max, flatten, remove_duplicates, remove_evens


def test_find_max():
    assert find_max([3, 9, 2]) == 9
    assert find_max([7]) == 7


def test_find_max_with_only_negative_numbers():
    assert find_max([-8, -3, -20]) == -3
    assert find_max([-5]) == -5


def test_find_max_of_empty_list_is_none():
    assert find_max([]) is None


def test_remove_evens():
    assert remove_evens([1, 2, 3, 4, 5, 6]) == [1, 3, 5]


def test_remove_evens_when_all_are_even():
    assert remove_evens([2, 4, 6, 8]) == []


def test_remove_evens_does_not_change_the_input():
    original = [1, 2, 3, 4]
    remove_evens(original)
    assert original == [1, 2, 3, 4]


def test_chunk():
    assert chunk([1, 2, 3, 4, 5], 2) == [[1, 2], [3, 4], [5]]
    assert chunk([1, 2, 3, 4], 2) == [[1, 2], [3, 4]]


def test_chunk_when_size_is_bigger_than_the_list():
    assert chunk([1, 2], 5) == [[1, 2]]


def test_chunk_of_empty_list():
    assert chunk([], 3) == []


@pytest.mark.parametrize("size", [0, -2])
def test_chunk_rejects_bad_sizes(size):
    with pytest.raises(ValueError):
        chunk([1, 2, 3], size)


def test_remove_duplicates_keeps_first_occurrence_in_order():
    assert remove_duplicates([3, 1, 3, 2, 1]) == [3, 1, 2]
    assert remove_duplicates(["b", "a", "b"]) == ["b", "a"]


def test_flatten_one_level():
    assert flatten([[1, 2], [3], []]) == [1, 2, 3]
    assert flatten([]) == []
