from programs.fizzbuzz import fizzbuzz


def test_first_five_numbers():
    assert fizzbuzz(5) == ["1", "2", "Fizz", "4", "Buzz"]


def test_the_list_has_one_entry_per_number_including_n():
    assert len(fizzbuzz(10)) == 10
    assert fizzbuzz(3)[-1] == "Fizz"


def test_multiples_of_both_three_and_five_are_fizzbuzz():
    result = fizzbuzz(30)
    assert result[14] == "FizzBuzz"
    assert result[29] == "FizzBuzz"


def test_full_sequence_up_to_fifteen():
    assert fizzbuzz(15) == ["1", "2", "Fizz", "4", "Buzz", "Fizz", "7", "8", "Fizz", "Buzz",
                            "11", "Fizz", "13", "14", "FizzBuzz"]


def test_one_gives_a_single_entry():
    assert fizzbuzz(1) == ["1"]


def test_zero_and_negative_give_an_empty_list():
    assert fizzbuzz(0) == []
    assert fizzbuzz(-4) == []
