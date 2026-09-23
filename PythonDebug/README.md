# Python Debugging Practice: Simple Programs

Ten small Python programs. Every one of them is supposed to behave exactly as described in the
docstring at the top of its file, and every one of them currently has problems. Your job: find
them and fix them, until **all tests pass**.

This is a blind exercise: there is no list of bugs and no hints about where they are or how many
there are. The tests tell you *what* is wrong; you work out *why*.

## Setup

Requires Python 3.9+ (checked on 3.13).

```bash
python -m pip install -r requirements.txt     # installs pytest
python -m pytest                              # run all the tests
```

## The programs (`programs/`)

Suggested order, easiest first:

| Level | Programs |
|---|---|
| 1 | `fizzbuzz`, `temperature`, `grades`, `primes` |
| 2 | `string_tools`, `list_utils`, `calculator` |
| 3 | `shopping_cart`, `bank_account`, `todo` |

The **docstring at the top of each file is the specification**: it says what every function must
do, including the edge cases. The tests in `tests/` check exactly that.

## Ground rules
1. **Do not edit anything in `tests/`.** The tests are correct; the programs are wrong.
2. Fix the cause, not the symptom. Do not special-case a test's exact input.
3. Work on **one failing test at a time**, then re-run everything.
4. When all tests pass, run `python run_demos.py` and read the output: does it all look right?

## How to run things

```bash
python -m pytest                        # everything
python -m pytest tests/test_grades.py   # one file
python -m pytest -k best_student        # tests whose name contains "best_student"
python -m pytest -x                     # stop at the first failure
python -m pytest -v                     # one line per test
python -m pytest -l                     # show local variables when a test fails
python -m pytest --pdb                  # drop into the debugger at a failure

python -m programs.fizzbuzz             # run one program's demo
python run_demos.py                     # run every demo (a crash is shown, then it carries on)
python run_demos.py todo primes         # just some of them
python -m pytest > output.txt 2>&1      # save the whole output to a file
```

## Reading a failure

```
    def test_zero_degrees_is_freezing():
>       assert describe_temperature(0) == "freezing"
E       AssertionError: assert 'cold' == 'freezing'
```
- `>` marks the line that failed. `E` lines say what was expected and what you actually got.
- For a crash, read the **last** lines of the traceback first (the error type and message), then
  work upwards to the first line that is in *your* code.

## Debugging tools you can use
- `print(...)` inside a function to see a value while it runs (remove it afterwards).
- `breakpoint()` on a line: run the test, and Python stops there. Useful commands: `n` (next line),
  `s` (step into a call), `p variable` (print), `c` (continue), `q` (quit).
- The debugger in VS Code / PyCharm: set a breakpoint, run the failing test in debug mode, step, and
  watch the variables.
- Try the function by hand in the Python prompt: `python`, then
  `from programs.grades import letter_grade` and `letter_grade(90)`.

## Done means
`python -m pytest` reports every test as passed, and the demos look right.

When you are finished (or genuinely stuck), ask for the answer key.
