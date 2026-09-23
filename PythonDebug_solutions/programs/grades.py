"""Grade calculator.

average(scores)
    The average of a list of scores. For an empty list it returns 0.0.

letter_grade(score)
    90 or above -> "A", 80 or above -> "B", 70 or above -> "C", 60 or above -> "D",
    anything lower -> "F". A score below 0 or above 100 raises ValueError.

pass_rate(scores, passing=60)
    The percentage (0 to 100) of scores that are at least `passing`, rounded to 1 decimal.
    For an empty list it returns 0.0.

best_student(results)
    `results` is a dict such as {"amy": 90, "bob": 75}. Returns the name with the highest score.
    If several students share the highest score, the name that comes first alphabetically wins.
    For an empty dict it returns None.
"""


def average(scores):
    if not scores:
        return 0.0
    return sum(scores) / len(scores)


def letter_grade(score):
    if score < 0 or score > 100:
        raise ValueError("score must be between 0 and 100")
    if score >= 90:
        return "A"
    if score >= 80:
        return "B"
    if score >= 70:
        return "C"
    if score >= 60:
        return "D"
    return "F"


def pass_rate(scores, passing=60):
    if not scores:
        return 0.0
    passed = 0
    for score in scores:
        if score >= passing:
            passed += 1
    return round(passed / len(scores) * 100, 1)


def best_student(results):
    best_name = None
    best_score = None
    for name in sorted(results):
        score = results[name]
        if best_score is None or score > best_score:
            best_name = name
            best_score = score
    return best_name


def main():
    scores = [95, 82, 71, 64, 40]
    print("Average:", average(scores))
    print("Grades:", [letter_grade(s) for s in scores])
    print("Pass rate:", pass_rate(scores), "%")
    print("Best:", best_student({"amy": 90, "bob": 90, "cara": 70}))
    print("Best when everyone scored zero:", best_student({"amy": 0, "bob": 0}))


if __name__ == "__main__":
    main()
