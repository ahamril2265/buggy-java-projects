from programs.todo import TodoList


def make_list():
    todo = TodoList()
    todo.add("write report", 2)
    todo.add("buy milk", 1)
    todo.add("call mum", 3)
    return todo


def test_pending_is_ordered_by_priority_highest_first():
    assert make_list().pending() == ["call mum", "write report", "buy milk"]


def test_same_priority_is_alphabetical():
    todo = TodoList()
    todo.add("zebra", 1)
    todo.add("apple", 1)
    todo.add("mango", 1)
    assert todo.pending() == ["apple", "mango", "zebra"]


def test_complete_moves_a_task_from_pending_to_completed():
    todo = make_list()
    assert todo.complete("buy milk") is True
    assert todo.pending() == ["call mum", "write report"]
    assert todo.completed() == ["buy milk"]


def test_complete_finds_a_title_that_was_built_at_run_time():
    todo = TodoList()
    todo.add("buy milk")
    title = "".join(["buy", " ", "milk"])
    assert todo.complete(title) is True
    assert todo.completed() == ["buy milk"]


def test_completing_an_unknown_task_returns_false():
    todo = make_list()
    assert todo.complete("fly to the moon") is False
    assert todo.completed() == []


def test_completed_keeps_the_order_the_tasks_were_added():
    todo = make_list()
    todo.complete("call mum")
    todo.complete("write report")
    assert todo.completed() == ["write report", "call mum"]


def test_summary():
    todo = make_list()
    assert todo.summary() == "3 pending, 0 done"
    todo.complete("buy milk")
    assert todo.summary() == "2 pending, 1 done"


def test_empty_list():
    todo = TodoList()
    assert todo.pending() == []
    assert todo.summary() == "0 pending, 0 done"
