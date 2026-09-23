import pytest

from programs.grades import average, best_student, letter_grade, pass_rate


def test_average():
    assert average([80, 90, 100]) == 90
    assert average([1, 2]) == 1.5


def test_average_of_empty_list_is_zero():
    assert average([]) == 0.0


@pytest.mark.parametrize("score, grade", [
    (100, "A"), (95, "A"), (90, "A"),
    (89, "B"), (80, "B"),
    (79, "C"), (70, "C"),
    (69, "D"), (60, "D"),
    (59, "F"), (0, "F"),
])
def test_letter_grade(score, grade):
    assert letter_grade(score) == grade


@pytest.mark.parametrize("bad_score", [-1, 101, 250])
def test_letter_grade_rejects_impossible_scores(bad_score):
    with pytest.raises(ValueError):
        letter_grade(bad_score)


def test_pass_rate():
    assert pass_rate([95, 82, 71, 64, 40]) == 80.0
    assert pass_rate([60, 60, 59]) == 66.7


def test_pass_rate_uses_the_given_passing_mark():
    assert pass_rate([50, 70, 90], passing=70) == 66.7


def test_pass_rate_of_empty_list_is_zero():
    assert pass_rate([]) == 0.0


def test_best_student():
    assert best_student({"amy": 90, "bob": 75, "cara": 60}) == "amy"


def test_best_student_tie_goes_to_first_alphabetically():
    assert best_student({"cara": 90, "bob": 90, "amy": 70}) == "bob"


def test_best_student_when_everyone_scored_zero():
    assert best_student({"bob": 0, "amy": 0}) == "amy"


def test_best_student_of_nobody_is_none():
    assert best_student({}) is None
