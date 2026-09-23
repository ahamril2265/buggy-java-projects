# Answer key: PythonDebug (22 bugs in 10 programs)

Do not read this before you have tried the exercise. The correct programs are in `programs/` here;
`../PythonDebug/programs/` is the buggy copy. All 140 tests pass on the correct version; 49 fail on
the buggy one.

For each bug: where, what it does, the fix, and the Python idea behind it.

---

## Level 1

### fizzbuzz.py: `fizzbuzz`
1. **`range(1, n)` stops one short.** `range(a, b)` **excludes** `b`. To include `n` write
   `range(1, n + 1)`. (Off-by-one; the most common bug in any language.)
2. **The `% 15` check comes last, so it can never run.** A number divisible by 15 is also divisible
   by 3, so the `% 3` branch catches it first and prints "Fizz". In an `if / elif` chain the **most
   specific condition must come first**.

### temperature.py
3. **`fahrenheit - 32 * 5 / 9` instead of `(fahrenheit - 32) * 5 / 9`.** Multiplication happens before
   subtraction, so only the `32` got scaled. Use parentheses to force the order you mean.
4. **`celsius < 0` instead of `celsius <= 0`.** The spec says "0 or below". Boundary values (`0`, `10`,
   `20`, `30`) are where `<` vs `<=` bugs hide, so always test the exact boundary.

### grades.py
5. **`letter_grade` used `>` instead of `>=`.** A score of exactly 90 is an "A", not a "B". Same
   boundary idea as bug 4.
6. **`best_student` started with `best_score = 0`.** If every score is 0 nothing is ever "greater than 0",
   so the answer stays `None`. Use `None` as "nothing seen yet" and test `best_score is None or ...`.
   General rule: an initial value must not be a value the data can legitimately have.

### primes.py
7. **`range(2, int(n ** 0.5))` leaves out the square root itself.** For `4`, `int(4 ** 0.5)` is 2 and
   `range(2, 2)` is empty, so 4 looked prime; same for 9, 25, 49. Fix: `int(n ** 0.5) + 1`.
8. **`range(2, n)` in `primes_up_to` excludes `n`.** Fix: `range(2, n + 1)`.
   (Bugs 7 and 8 are *layered*: `primes_up_to(30)` fails for both reasons until you fix both.)

---

## Level 2

### string_tools.py
9. **`is_palindrome` only lower-cased the text.** It never removed spaces and punctuation, so
   "A man, a plan, a canal: Panama" was rejected. Keep only letters and digits: `character.isalnum()`.
10. **`text.split(" ")` instead of `text.split()`.** Splitting on a single space produces empty strings
    for repeated spaces (`"a  b".split(" ")` is `["a", "", "b"]`), and `"".split(" ")` is `[""]`, which
    has length 1. `split()` with **no argument** splits on any run of whitespace and drops empties.
11. **`text.title()` capitalises after an apostrophe.** `"it's".title()` is `"It'S"`. `str.title()` treats
    every non-letter as a word break. Capitalise each word yourself: `word[:1].upper() + word[1:].lower()`.

### list_utils.py
12. **`find_max` started at `0`.** With only negative numbers, `0` beats all of them and is wrongly
    returned. Start from the **first element** (`numbers[0]`).
13. **`remove_evens` removed items while looping over the same list.** Removing shifts the remaining
    items left, so the loop skips the next element (`[2, 4, 6, 8]` became `[4, 8]`). It also changed the
    caller's list. Build a **new** list: `[n for n in numbers if n % 2 != 0]`.
14. **`chunk` used `range(0, len(items) - size + 1, size)`.** That stops before the last, shorter chunk
    (`[1,2,3,4,5]`, size 2 lost `[5]`) and returns `[]` when `size > len(items)`. The right range is
    `range(0, len(items), size)`; slicing past the end is safe in Python.

### calculator.py
15. **Subtraction returned `b - a`.** Order matters for `-` and `/`. Copy-paste or "mirror image" slip.
16. **`_to_number` only used `int()`.** `int("2.5")` raises `ValueError`. Try `int` first and fall back to
    `float`.

---

## Level 3

### shopping_cart.py
17. **`items = []` at class level.** A variable defined in the class body belongs to the **class** and is
    shared by every instance, so all carts share one list (and tests leak state into each other).
    Create per-instance state in `__init__`: `self.items = []`.
18. **`def add_item(item, items=[])`: the mutable default argument.** The default list is created **once**,
    when the function is defined, and reused on every call, so items pile up between calls. Use
    `items=None` and create the list inside: `if items is None: items = []`.

### bank_account.py
19. **`amount >= self.balance` in `withdraw`.** Withdrawing exactly the balance is allowed, so only
    `amount > self.balance` is an error. (Boundary again.)
20. **`transfer_to` deposited before withdrawing.** If the withdrawal then failed, the receiver had
    already been credited: money created out of nothing. Do the step that can **fail** first
    (`withdraw`), then the step that cannot (`deposit`), so a failure leaves nothing half-done.

### todo.py
21. **`task["title"] is title` instead of `==`.** `is` asks "the very same object?", `==` asks "equal
    contents?". Two equal strings built at different times are different objects, so `is` was `False`
    for a title assembled at run time. Use `==` to compare values.
22. **`ordered = open_tasks.sort(...)`.** `list.sort()` sorts **in place** and returns `None`. Either
    call `open_tasks.sort(...)` and then use `open_tasks`, or use `sorted(open_tasks, key=...)` which
    returns a new list.

---

## Patterns worth remembering
| Pattern | Bugs |
|---|---|
| Off-by-one / boundaries (`<` vs `<=`, `range` end) | 1, 4, 5, 7, 8, 14, 19 |
| Order of operations / order of checks | 2, 3, 20 |
| Wrong initial value | 6, 12 |
| Mutating while iterating / in-place vs new list | 13, 22 |
| Shared mutable state | 17, 18 |
| Value vs identity (`==` vs `is`) | 21 |
| Standard-library behaviour you must know | 10, 11, 16, 22 |
