import pytest

from programs.string_tools import count_words, is_palindrome, reverse_words, title_case


@pytest.mark.parametrize("text", ["racecar", "level", "a", "", "Racecar"])
def test_simple_palindromes(text):
    assert is_palindrome(text)


@pytest.mark.parametrize("text", [
    "A man, a plan, a canal: Panama",
    "Was it a car or a cat I saw?",
    "No lemon, no melon",
])
def test_palindromes_ignore_spaces_and_punctuation(text):
    assert is_palindrome(text)


@pytest.mark.parametrize("text", ["hello", "python", "ab"])
def test_non_palindromes(text):
    assert not is_palindrome(text)


def test_count_words():
    assert count_words("hello world") == 2
    assert count_words("one") == 1


def test_count_words_ignores_extra_whitespace():
    assert count_words("  the   quick brown fox  ") == 4
    assert count_words("tabs\tand\nnewlines") == 3


def test_count_words_in_empty_text_is_zero():
    assert count_words("") == 0
    assert count_words("   ") == 0


def test_reverse_words():
    assert reverse_words("hello big world") == "world big hello"
    assert reverse_words("single") == "single"
    assert reverse_words("a   b") == "b a"


def test_title_case():
    assert title_case("hello wORLD") == "Hello World"
    assert title_case("") == ""


def test_title_case_does_not_capitalise_after_an_apostrophe():
    assert title_case("it's o'neil") == "It's O'neil"
