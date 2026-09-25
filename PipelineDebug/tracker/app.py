"""The Debug Tracker window. Start it with:  python tracker_gui.py"""
import queue
import threading
import tkinter as tk
from tkinter import messagebox, ttk

from tracker import core

GREEN, GREEN_BG = "#2e7d32", "#e3f4e4"
RED, RED_BG = "#c62828", "#fde8e8"
GRAY, GRAY_BG = "#6b7280", "#eef0f3"
AMBER, AMBER_BG = "#b26a00", "#fff3d6"
BLUE = "#1f5fbf"


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Pipeline Debug Tracker")
        width = min(1220, self.winfo_screenwidth() - 60)
        height = min(800, self.winfo_screenheight() - 90)
        self.geometry(f"{width}x{height}+20+20")
        self.minsize(900, 600)

        self.results = []
        self.fixed_now, self.broken_now = [], []
        self.history = core.RunHistory()
        self.last_run = core.LastRun()
        self.bugs = core.BugLog()
        self.running = False
        self.inbox = queue.Queue()
        self.current_bug_id = None
        self.stage_boxes = {}

        self._style()
        self._build_topbar()
        self.tabs = ttk.Notebook(self)
        self.tabs.pack(fill="both", expand=True, padx=10, pady=(0, 10))
        self._build_dashboard()
        self._build_board()
        self._build_bugs()
        self._build_history()
        self._build_guide()

        self._refresh_all()
        self.after(150, self.run_tests)
        self.after(120, self._poll)

    # ------------------------------------------------------------------ styling
    def _style(self):
        style = ttk.Style(self)
        if "clam" in style.theme_names():
            style.theme_use("clam")
        style.configure("Treeview", rowheight=24, font=("Segoe UI", 10))
        style.configure("Treeview.Heading", font=("Segoe UI", 10, "bold"))
        style.configure("TNotebook.Tab", padding=(16, 7), font=("Segoe UI", 10, "bold"))
        style.configure("Big.TButton", padding=(14, 6), font=("Segoe UI", 10, "bold"))
        style.configure("green.Horizontal.TProgressbar", troughcolor="#dfe3e8", background=GREEN)
        style.configure("red.Horizontal.TProgressbar", troughcolor="#dfe3e8", background=RED)

    # ------------------------------------------------------------------ top bar
    def _build_topbar(self):
        bar = ttk.Frame(self, padding=(12, 10))
        bar.pack(fill="x")
        ttk.Label(bar, text="Pipeline Debug Tracker", font=("Segoe UI", 15, "bold")).pack(side="left")
        self.run_button = ttk.Button(bar, text="▶  Run all tests", style="Big.TButton",
                                     command=self.run_tests)
        self.run_button.pack(side="left", padx=(22, 6))
        self.rerun_file_button = ttk.Button(bar, text="Run selected file", command=self.run_selected_file)
        self.rerun_file_button.pack(side="left", padx=3)
        self.rerun_test_button = ttk.Button(bar, text="Re-run selected test", command=self.run_selected_test)
        self.rerun_test_button.pack(side="left", padx=3)
        self.status_label = ttk.Label(bar, text="starting…", foreground=GRAY)
        self.status_label.pack(side="right")
        self.summary_label = ttk.Label(bar, text="", font=("Segoe UI", 11, "bold"))
        self.summary_label.pack(side="right", padx=18)

    # ------------------------------------------------------------------ dashboard
    def _build_dashboard(self):
        tab = ttk.Frame(self.tabs, padding=12)
        self.tabs.add(tab, text="  Dashboard  ")

        cards = ttk.Frame(tab)
        cards.pack(fill="x")
        self.card_labels = {}
        for key, title, color in (("passed", "PASSING", GREEN), ("failed", "FAILING", RED),
                                  ("total", "TOTAL TESTS", GRAY), ("percent", "COMPLETE", BLUE),
                                  ("bugs", "BUGS FIXED (LOG)", AMBER)):
            frame = tk.Frame(cards, bg="white", highlightbackground="#d0d5dd", highlightthickness=1)
            frame.pack(side="left", fill="x", expand=True, padx=4)
            tk.Label(frame, text=title, bg="white", fg=GRAY, font=("Segoe UI", 8, "bold")).pack(pady=(8, 0))
            value = tk.Label(frame, text="–", bg="white", fg=color, font=("Segoe UI", 24, "bold"))
            value.pack(pady=(0, 8))
            self.card_labels[key] = value

        self.progress = ttk.Progressbar(tab, maximum=100, style="green.Horizontal.TProgressbar")
        self.progress.pack(fill="x", padx=4, pady=(14, 4))

        ttk.Label(tab, text="Pipeline map  -  click a stage to see its tests",
                  font=("Segoe UI", 11, "bold")).pack(anchor="w", pady=(10, 2))
        self.map_canvas = tk.Canvas(tab, height=250, bg="white", highlightthickness=1,
                                    highlightbackground="#d0d5dd")
        self.map_canvas.pack(fill="x", padx=4)
        self.map_canvas.bind("<Configure>", lambda e: self._draw_map())
        self.map_canvas.bind("<Button-1>", self._map_click)

        lower = ttk.Frame(tab)
        lower.pack(fill="both", expand=True, pady=(12, 0))
        left = ttk.LabelFrame(lower, text=" Fixed since the previous run ", padding=6)
        left.pack(side="left", fill="both", expand=True, padx=(4, 6))
        self.fixed_list = tk.Listbox(left, height=6, bd=0, highlightthickness=0, fg=GREEN,
                                     font=("Consolas", 9))
        self.fixed_list.pack(fill="both", expand=True)
        right = ttk.LabelFrame(lower, text=" Newly failing since the previous run ", padding=6)
        right.pack(side="left", fill="both", expand=True, padx=(6, 4))
        self.broken_list = tk.Listbox(right, height=6, bd=0, highlightthickness=0, fg=RED,
                                      font=("Consolas", 9))
        self.broken_list.pack(fill="both", expand=True)

    def _draw_map(self):
        canvas = self.map_canvas
        canvas.delete("all")
        self.stage_boxes = {}
        width = max(canvas.winfo_width(), 700)
        health = core.stage_health(self.results)
        count = len(core.STAGES)
        gap = 34
        box_w = (width - 40 - gap * (count - 1)) / count
        top, box_h = 20, 84

        def paint(x0, y0, x1, y1, label, sub, file):
            passed, total = health.get(file, (0, 0))
            if total == 0:
                fill, edge, text = GRAY_BG, GRAY, "not run"
            elif passed == total:
                fill, edge, text = GREEN_BG, GREEN, f"{passed}/{total}  ✔"
            else:
                fill, edge, text = RED_BG, RED, f"{passed}/{total}  ✘ {total - passed} failing"
            canvas.create_rectangle(x0, y0, x1, y1, fill=fill, outline=edge, width=2)
            canvas.create_text((x0 + x1) / 2, y0 + 24, text=label, font=("Segoe UI", 12, "bold"), fill="#1f2937")
            canvas.create_text((x0 + x1) / 2, y0 + 46, text=sub, font=("Consolas", 9), fill=GRAY)
            canvas.create_text((x0 + x1) / 2, y0 + 66, text=text, font=("Segoe UI", 9, "bold"), fill=edge)
            self.stage_boxes[file] = (x0, y0, x1, y1)

        for index, (label, test_file, source) in enumerate(core.STAGES):
            x0 = 20 + index * (box_w + gap)
            paint(x0, top, x0 + box_w, top + box_h, f"{index + 1}. {label}", source, test_file)
            if index < count - 1:
                canvas.create_line(x0 + box_w + 4, top + box_h / 2, x0 + box_w + gap - 4, top + box_h / 2,
                                   arrow="last", width=2, fill=GRAY)
        paint(20, top + box_h + 40, width - 20, top + box_h + 40 + box_h,
              "End-to-end  (runner.py)", "the whole pipeline on data/*.csv", core.E2E_FILE)
        canvas.create_line(width / 2, top + box_h + 2, width / 2, top + box_h + 38, arrow="last",
                           width=2, fill=GRAY, dash=(4, 3))

    def _map_click(self, event):
        for file, (x0, y0, x1, y1) in self.stage_boxes.items():
            if x0 <= event.x <= x1 and y0 <= event.y <= y1:
                self.file_filter.set(file)
                self.status_filter.set("all")
                self.search_var.set("")
                self._refresh_board()
                self.tabs.select(1)
                return

    # ------------------------------------------------------------------ test board
    def _build_board(self):
        tab = ttk.Frame(self.tabs, padding=10)
        self.tabs.add(tab, text="  Test Board  ")

        filters = ttk.Frame(tab)
        filters.pack(fill="x", pady=(0, 8))
        self.status_filter = tk.StringVar(value="all")
        for value, label in (("all", "All"), ("FAIL", "Failing"), ("PASS", "Passing")):
            ttk.Radiobutton(filters, text=label, value=value, variable=self.status_filter,
                            command=self._refresh_board).pack(side="left", padx=(0, 10))
        ttk.Label(filters, text="File:").pack(side="left", padx=(14, 4))
        self.file_filter = tk.StringVar(value="all")
        self.file_box = ttk.Combobox(filters, textvariable=self.file_filter, state="readonly", width=26,
                                     values=["all"])
        self.file_box.pack(side="left")
        self.file_box.bind("<<ComboboxSelected>>", lambda e: self._refresh_board())
        ttk.Label(filters, text="Search:").pack(side="left", padx=(14, 4))
        self.search_var = tk.StringVar()
        self.search_var.trace_add("write", lambda *a: self._refresh_board())
        ttk.Entry(filters, textvariable=self.search_var, width=26).pack(side="left")
        ttk.Button(filters, text="Log selected as bug", command=self.log_selected_as_bug).pack(side="right")
        self.board_count = ttk.Label(filters, text="", foreground=GRAY)
        self.board_count.pack(side="right", padx=14)

        columns = ("status", "file", "test", "where", "error")
        holder = ttk.Frame(tab)
        holder.pack(fill="both", expand=True)
        self.board = ttk.Treeview(holder, columns=columns, show="headings", selectmode="browse")
        for name, title, width in (("status", "Status", 70), ("file", "File", 170), ("test", "Test / function", 330),
                                   ("where", "Raised at", 190), ("error", "Error produced", 420)):
            self.board.heading(name, text=title)
            self.board.column(name, width=width, anchor="w", stretch=name in ("error", "test"))
        self.board.tag_configure("FAIL", background=RED_BG, foreground="#7f1d1d")
        self.board.tag_configure("PASS", background="white", foreground="#1f2937")
        scroll = ttk.Scrollbar(holder, orient="vertical", command=self.board.yview)
        self.board.configure(yscrollcommand=scroll.set)
        self.board.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        self.board.bind("<<TreeviewSelect>>", self._show_detail)

        detail_box = ttk.LabelFrame(tab, text=" Failure detail ", padding=4)
        detail_box.pack(fill="x", pady=(8, 0))
        self.detail = tk.Text(detail_box, height=9, wrap="none", font=("Consolas", 9), bg="#fafafa",
                              state="disabled")
        self.detail.pack(fill="x")

    def _visible_results(self):
        wanted_status = self.status_filter.get()
        wanted_file = self.file_filter.get()
        needle = self.search_var.get().strip().lower()
        rows = []
        for r in self.results:
            if wanted_status != "all" and r["status"] != wanted_status:
                continue
            if wanted_file != "all" and r["file"] != wanted_file:
                continue
            if needle and needle not in (r["file"] + r["test"] + r["error"]).lower():
                continue
            rows.append(r)
        rows.sort(key=lambda r: (r["status"] != "FAIL", r["file"]))
        return rows

    def _refresh_board(self):
        files = ["all"] + sorted({r["file"] for r in self.results})
        self.file_box.configure(values=files)
        selected_key = self._selected_key()
        self.board.delete(*self.board.get_children())
        rows = self._visible_results()
        for r in rows:
            mark = "✘ FAIL" if r["status"] == "FAIL" else "✔ PASS"
            self.board.insert("", "end", iid=core.result_key(r), tags=(r["status"],),
                              values=(mark, r["file"], r["test"], r["where"], r["error"]))
        self.board_count.configure(text=f"showing {len(rows)} of {len(self.results)}")
        if selected_key and self.board.exists(selected_key):
            self.board.selection_set(selected_key)
        self._show_detail()

    def _selected_key(self):
        selection = self.board.selection()
        return selection[0] if selection else None

    def _result_by_key(self, key):
        return next((r for r in self.results if core.result_key(r) == key), None)

    def _show_detail(self, _event=None):
        result = self._result_by_key(self._selected_key()) if self._selected_key() else None
        self.detail.configure(state="normal")
        self.detail.delete("1.0", "end")
        if result is None:
            self.detail.insert("end", "Select a test to see its full failure text.")
        elif result["status"] == "PASS":
            self.detail.insert("end", f"{result['file']}::{result['test']}\nPASS  ({result['seconds']:.3f}s)")
        else:
            self.detail.insert("end", f"{result['file']}::{result['test']}\n\n{result['detail']}")
        self.detail.configure(state="disabled")

    # ------------------------------------------------------------------ bug log
    def _build_bugs(self):
        tab = ttk.Frame(self.tabs, padding=10)
        self.tabs.add(tab, text="  Bug Log  ")
        panes = ttk.PanedWindow(tab, orient="horizontal")
        panes.pack(fill="both", expand=True)

        left = ttk.Frame(panes)
        panes.add(left, weight=3)
        top = ttk.Frame(left)
        top.pack(fill="x", pady=(0, 6))
        ttk.Label(top, text="Show:").pack(side="left")
        self.bug_filter = tk.StringVar(value="all")
        box = ttk.Combobox(top, textvariable=self.bug_filter, state="readonly", width=14,
                           values=["all"] + core.BUG_STATUSES)
        box.pack(side="left", padx=6)
        box.bind("<<ComboboxSelected>>", lambda e: self._refresh_bugs())
        ttk.Button(top, text="+ New bug", command=self.new_bug).pack(side="right")
        self.bug_counts = ttk.Label(top, text="", foreground=GRAY)
        self.bug_counts.pack(side="right", padx=12)

        cols = ("id", "title", "module", "status", "test")
        self.bug_tree = ttk.Treeview(left, columns=cols, show="headings", selectmode="browse")
        for name, title, width in (("id", "#", 36), ("title", "Title", 230), ("module", "Module", 80),
                                   ("status", "Status", 90), ("test", "Linked test now", 110)):
            self.bug_tree.heading(name, text=title)
            self.bug_tree.column(name, width=width, anchor="w", stretch=name == "title")
        self.bug_tree.tag_configure("Fixed", foreground=GREEN)
        self.bug_tree.tag_configure("Found", foreground=BLUE)
        self.bug_tree.tag_configure("Investigating", foreground=AMBER)
        self.bug_tree.pack(fill="both", expand=True)
        self.bug_tree.bind("<<TreeviewSelect>>", self._load_bug_form)

        right = ttk.Frame(panes, padding=(12, 0, 0, 0))
        panes.add(right, weight=4)
        self.form = {}
        row = ttk.Frame(right)
        row.pack(fill="x")
        ttk.Label(row, text="Title").pack(anchor="w")
        self.form["title"] = ttk.Entry(row)
        self.form["title"].pack(fill="x", pady=(0, 6))
        meta = ttk.Frame(right)
        meta.pack(fill="x")
        for name, label, values in (("module", "Module", core.MODULES), ("category", "Category", core.CATEGORIES),
                                    ("status", "Status", core.BUG_STATUSES)):
            cell = ttk.Frame(meta)
            cell.pack(side="left", fill="x", expand=True, padx=(0, 6))
            ttk.Label(cell, text=label).pack(anchor="w")
            self.form[name] = ttk.Combobox(cell, values=values, state="readonly")
            self.form[name].pack(fill="x")
        ttk.Label(right, text="Linked test (file::test, optional)").pack(anchor="w", pady=(6, 0))
        self.form["linked_test"] = ttk.Entry(right)
        self.form["linked_test"].pack(fill="x", pady=(0, 4))
        for name, label, height in (("hypothesis", "My hypothesis - what do I think is wrong?", 3),
                                    ("root_cause", "Root cause - what was really wrong?", 3),
                                    ("fix", "The fix - what did I change?", 3),
                                    ("learned", "What I learned", 3)):
            ttk.Label(right, text=label).pack(anchor="w", pady=(6, 0))
            text = tk.Text(right, height=height, wrap="word", font=("Segoe UI", 10), bd=1, relief="solid")
            text.pack(fill="both", expand=True)
            self.form[name] = text
        buttons = ttk.Frame(right)
        buttons.pack(fill="x", pady=(10, 0))
        ttk.Button(buttons, text="Save", style="Big.TButton", command=self.save_bug).pack(side="left")
        ttk.Button(buttons, text="Mark as Fixed", command=self.mark_fixed).pack(side="left", padx=6)
        ttk.Button(buttons, text="Delete", command=self.delete_bug).pack(side="right")
        self.bug_saved_label = ttk.Label(buttons, text="", foreground=GREEN)
        self.bug_saved_label.pack(side="left", padx=10)

    def _bug_test_state(self, entry):
        key = entry.get("linked_test", "")
        if not key:
            return "–"
        result = self._result_by_key(key)
        return {None: "not found", "PASS": "✔ PASS", "FAIL": "✘ FAIL"}.get(result["status"] if result else None, "?")

    def _refresh_bugs(self):
        wanted = self.bug_filter.get()
        self.bug_tree.delete(*self.bug_tree.get_children())
        for entry in self.bugs.entries:
            if wanted != "all" and entry["status"] != wanted:
                continue
            self.bug_tree.insert("", "end", iid=str(entry["id"]), tags=(entry["status"],),
                                 values=(entry["id"], entry["title"], entry["module"], entry["status"],
                                         self._bug_test_state(entry)))
        counts = self.bugs.counts()
        self.bug_counts.configure(text="  ".join(f"{k}: {v}" for k, v in counts.items() if v))
        self.card_labels["bugs"].configure(text=f"{counts.get('Fixed', 0)} / {len(self.bugs.entries)}")
        if self.current_bug_id and self.bug_tree.exists(str(self.current_bug_id)):
            self.bug_tree.selection_set(str(self.current_bug_id))

    def _read_form(self):
        data = {}
        for name, widget in self.form.items():
            data[name] = widget.get("1.0", "end").strip() if isinstance(widget, tk.Text) else widget.get().strip()
        return data

    def _write_form(self, entry):
        for name, widget in self.form.items():
            value = entry.get(name, "") if entry else ""
            if isinstance(widget, tk.Text):
                widget.delete("1.0", "end")
                widget.insert("1.0", value)
            elif isinstance(widget, ttk.Combobox):
                widget.set(value)
            else:
                widget.delete(0, "end")
                widget.insert(0, value)

    def _load_bug_form(self, _event=None):
        selection = self.bug_tree.selection()
        if not selection:
            return
        self.current_bug_id = int(selection[0])
        self._write_form(self.bugs.get(self.current_bug_id))
        self.bug_saved_label.configure(text="")

    def new_bug(self, **preset):
        fields = {"title": "New bug", "status": "Suspected", **preset}
        entry = self.bugs.add(**fields)
        self.current_bug_id = entry["id"]
        self.bug_filter.set("all")
        self._refresh_bugs()
        self._write_form(entry)
        self.form["title"].focus_set()
        self.form["title"].select_range(0, "end")
        return entry

    def save_bug(self):
        if self.current_bug_id is None:
            self.new_bug()
        data = self._read_form()
        if not data["title"]:
            messagebox.showinfo("Bug Log", "Give the bug a title first.")
            return
        self.bugs.update(self.current_bug_id, **data)
        self._refresh_bugs()
        self.bug_saved_label.configure(text="saved ✔")

    def mark_fixed(self):
        if self.current_bug_id is None:
            return
        self.form["status"].set("Fixed")
        self.save_bug()

    def delete_bug(self):
        if self.current_bug_id is None:
            return
        if messagebox.askyesno("Delete bug", "Delete this bug log entry?"):
            self.bugs.delete(self.current_bug_id)
            self.current_bug_id = None
            self._write_form(None)
            self._refresh_bugs()

    def log_selected_as_bug(self):
        key = self._selected_key()
        result = self._result_by_key(key) if key else None
        if result is None:
            messagebox.showinfo("Bug Log", "Select a test in the board first.")
            return
        stage = result["file"].replace("test_", "").replace(".py", "")
        module = stage if stage in core.MODULES else ("runner" if "e2e" in stage else "unknown")
        self.tabs.select(2)
        self.new_bug(title=result["test"].replace("test_", "").replace("_", " "), module=module,
                     linked_test=key, status="Investigating",
                     hypothesis=f"Test says: {result['error']}" if result["error"] else "")

    # ------------------------------------------------------------------ history
    def _build_history(self):
        tab = ttk.Frame(self.tabs, padding=10)
        self.tabs.add(tab, text="  History  ")
        self.chart = tk.Canvas(tab, height=190, bg="white", highlightthickness=1,
                               highlightbackground="#d0d5dd")
        self.chart.pack(fill="x")
        self.chart.bind("<Configure>", lambda e: self._draw_chart())
        cols = ("time", "passed", "failed", "total", "percent", "fixed", "broken")
        self.history_tree = ttk.Treeview(tab, columns=cols, show="headings")
        for name, title, width in (("time", "When", 150), ("passed", "Pass", 70), ("failed", "Fail", 70),
                                   ("total", "Total", 70), ("percent", "% pass", 80),
                                   ("fixed", "Fixed", 70), ("broken", "Broke", 70)):
            self.history_tree.heading(name, text=title)
            self.history_tree.column(name, width=width, anchor="w")
        self.history_tree.pack(fill="both", expand=True, pady=(10, 0))

    def _draw_chart(self):
        canvas = self.chart
        canvas.delete("all")
        runs = self.history.runs[-40:]
        width, height = max(canvas.winfo_width(), 400), int(canvas["height"])
        canvas.create_text(10, 10, anchor="nw", text="Percent of tests passing, per run", fill=GRAY,
                           font=("Segoe UI", 9, "bold"))
        if not runs:
            return
        left, bottom, top = 40, height - 24, 34
        for pct in (0, 50, 100):
            y = bottom - (bottom - top) * pct / 100
            canvas.create_line(left, y, width - 10, y, fill="#e5e7eb")
            canvas.create_text(left - 6, y, anchor="e", text=f"{pct}%", fill=GRAY, font=("Segoe UI", 8))
        step = (width - left - 20) / max(len(runs), 1)
        for index, run in enumerate(runs):
            x0 = left + index * step + 3
            x1 = left + (index + 1) * step - 3
            y = bottom - (bottom - top) * run["percent"] / 100
            canvas.create_rectangle(x0, y, x1, bottom, fill=GREEN if run["failed"] == 0 else "#4f8ed8", outline="")

    # ------------------------------------------------------------------ guide
    def _build_guide(self):
        tab = ttk.Frame(self.tabs, padding=10)
        self.tabs.add(tab, text="  Guide  ")
        text = tk.Text(tab, wrap="word", font=("Segoe UI", 10), padx=14, pady=10, bd=0, bg="#fbfbfc")
        scroll = ttk.Scrollbar(tab, command=text.yview)
        text.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        text.pack(fill="both", expand=True)
        text.tag_configure("h", font=("Segoe UI", 11, "bold"), foreground=BLUE, spacing1=12, spacing3=3)
        for line in core.GUIDE_TEXT.splitlines():
            is_heading = line and not line.startswith((" ", "*")) and line.upper() == line
            text.insert("end", line + "\n", "h" if is_heading else "")
        text.configure(state="disabled")

    # ------------------------------------------------------------------ running tests
    def run_tests(self, targets=None, keyword=None):
        if self.running:
            return
        self.running = True
        for button in (self.run_button, self.rerun_file_button, self.rerun_test_button):
            button.state(["disabled"])
        self.status_label.configure(text="running tests…", foreground=AMBER)
        partial = bool(targets or keyword)

        def work():
            try:
                self.inbox.put(("done", partial, core.run_tests(targets, keyword)))
            except Exception as error:                       # shown in the status bar
                self.inbox.put(("error", partial, str(error)))

        threading.Thread(target=work, daemon=True).start()

    def run_selected_file(self):
        result = self._result_by_key(self._selected_key()) if self._selected_key() else None
        file = result["file"] if result else (self.file_filter.get() if self.file_filter.get() != "all" else None)
        if not file:
            messagebox.showinfo("Run", "Select a test (or pick a file in the filter) first.")
            return
        self.run_tests([f"tests/{file}"])

    def run_selected_test(self):
        key = self._selected_key()
        if not key:
            messagebox.showinfo("Run", "Select a test in the board first.")
            return
        file, name = key.split("::", 1)
        self.run_tests([f"tests/{file}::{name}"])

    def _poll(self):
        try:
            while True:
                kind, partial, payload = self.inbox.get_nowait()
                self._finish_run(kind, partial, payload)
        except queue.Empty:
            pass
        self.after(120, self._poll)

    def _finish_run(self, kind, partial, payload):
        self.running = False
        for button in (self.run_button, self.rerun_file_button, self.rerun_test_button):
            button.state(["!disabled"])
        if kind == "error":
            self.status_label.configure(text="run failed - see message", foreground=RED)
            messagebox.showerror("pytest", payload[:1500])
            return

        previous = self.last_run.load()
        if partial:
            merged = {core.result_key(r): r for r in self.results}
            merged.update({core.result_key(r): r for r in payload})
            self.results = list(merged.values())
        else:
            self.results = payload
        current = {core.result_key(r): r["status"] for r in self.results}
        self.fixed_now, self.broken_now = core.diff_results(previous, current)
        self.last_run.save(current)
        self.history.add(core.summarise(self.results), self.fixed_now, self.broken_now)
        self.status_label.configure(text=f"last run {core.now_text()}", foreground=GRAY)
        self._refresh_all()

    # ------------------------------------------------------------------ refresh everything
    def _refresh_all(self):
        summary = core.summarise(self.results)
        self.card_labels["passed"].configure(text=str(summary["passed"]) if self.results else "–")
        self.card_labels["failed"].configure(text=str(summary["failed"]) if self.results else "–")
        self.card_labels["total"].configure(text=str(summary["total"]) if self.results else "–")
        self.card_labels["percent"].configure(text=f"{summary['percent']:.0f}%" if self.results else "–")
        self.progress["value"] = summary["percent"]
        self.progress.configure(style="green.Horizontal.TProgressbar" if summary["failed"] == 0
                                else "red.Horizontal.TProgressbar")
        if self.results:
            self.summary_label.configure(
                text=f"PASS {summary['passed']}   FAIL {summary['failed']}",
                foreground=GREEN if summary["failed"] == 0 else RED)
        for widget, items in ((self.fixed_list, self.fixed_now), (self.broken_list, self.broken_now)):
            widget.delete(0, "end")
            for item in items or ["(none)"]:
                widget.insert("end", item)
        self._draw_map()
        self._refresh_board()
        self._refresh_bugs()
        self._draw_chart()
        self.history_tree.delete(*self.history_tree.get_children())
        for run in reversed(self.history.runs[-200:]):
            self.history_tree.insert("", "end", values=(run["time"], run["passed"], run["failed"],
                                                        run["total"], f"{run['percent']}%", run["fixed"], run["broken"]))


def main():
    App().mainloop()


if __name__ == "__main__":
    main()
