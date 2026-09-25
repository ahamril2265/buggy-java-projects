"""A to-do list.

TodoList
    add(title, priority=1)   Add a task. A higher priority number is more important.
    complete(title)          Mark the task with exactly that title as done. Returns True if it was
                             found, otherwise False.
    pending()                The titles of the tasks that are NOT done, most important first
                             (highest priority number first). Tasks with the same priority are in
                             alphabetical order.
    completed()              The titles of the tasks that are done, in the order they were added.
    summary()                Text like "2 pending, 1 done".
"""


class TodoList:
    def __init__(self):
        self.tasks = []

    def add(self, title, priority=1):
        self.tasks.append({"title": title, "priority": priority, "done": False})

    def complete(self, title):
        for task in self.tasks:
            if task["title"] == title:
                task["done"] = True
                return True
        return False

    def pending(self):
        open_tasks = [task for task in self.tasks if not task["done"]]
        ordered = sorted(open_tasks, key=lambda task: (-task["priority"], task["title"]))
        return [task["title"] for task in ordered]

    def completed(self):
        return [task["title"] for task in self.tasks if task["done"]]

    def summary(self):
        done = len(self.completed())
        pending = len(self.tasks) - done
        return f"{pending} pending, {done} done"


def main():
    todo = TodoList()
    todo.add("write report", 2)
    todo.add("buy milk", 1)
    todo.add("call mum", 3)
    todo.complete("buy milk")
    print("Pending:", todo.pending())
    print("Done:", todo.completed())
    print(todo.summary())


if __name__ == "__main__":
    main()
