"""Runs the small demo (main function) of every program so you can see what each one prints.

    python run_demos.py            # all programs
    python run_demos.py fizzbuzz   # just one
"""
import importlib
import sys

PROGRAMS = ["fizzbuzz", "temperature", "grades", "primes", "string_tools",
            "list_utils", "calculator", "shopping_cart", "bank_account", "todo"]

wanted = sys.argv[1:] or PROGRAMS
for name in wanted:
    print(f"===== {name} =====")
    try:
        importlib.import_module(f"programs.{name}").main()
    except Exception as error:  # a crash is information too; show it and carry on
        print(f"!! {type(error).__name__}: {error}")
    print()
