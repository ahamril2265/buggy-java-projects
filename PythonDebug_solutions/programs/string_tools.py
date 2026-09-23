"""String tools.

is_palindrome(text)
    True if the text reads the same forwards and backwards. Ignore upper/lower case, spaces and
    punctuation: "A man, a plan, a canal: Panama" is a palindrome. An empty text is a palindrome.

count_words(text)
    The number of words. Words are separated by any amount of whitespace (spaces, tabs, newlines).
    Leading and trailing whitespace is ignored. An empty text has 0 words.

reverse_words(text)
    The words in reverse order, joined by single spaces: "hello big world" -> "world big hello".

title_case(text)
    Capitalise the first letter of every space-separated word and make the rest of the word lower
    case: "hello wORLD" -> "Hello World". An apostrophe does not start a new word, so
    "it's o'neil" -> "It's O'neil".
"""


def is_palindrome(text):
    letters = [character.lower() for character in text if character.isalnum()]
    return letters == letters[::-1]


def count_words(text):
    return len(text.split())


def reverse_words(text):
    return " ".join(reversed(text.split()))


def title_case(text):
    words = text.split()
    return " ".join(word[:1].upper() + word[1:].lower() for word in words)


def main():
    print(is_palindrome("A man, a plan, a canal: Panama"))
    print(count_words("  the   quick brown fox  "))
    print(reverse_words("hello big world"))
    print(title_case("it's o'neil from THE north"))


if __name__ == "__main__":
    main()
