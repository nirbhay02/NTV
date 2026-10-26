"""
NTV Virtual TV Remote Controller for Android
Supports:
1. GUI Mode (Default): Floating TV remote with clickable buttons & live PC keyboard controls.
2. CLI Mode (--cli): Terminal mode with direct keypress detection.
"""

import sys
import os
import subprocess
import threading
import queue
import time

ADB_PATH = r"C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not os.path.exists(ADB_PATH):
    ADB_PATH = "adb"

DEFAULT_DEVICE = "192.168.0.199:37849"

# Android TV Keycodes
KEYCODES = {
    # D-pad & Navigation
    "UP": 19,
    "DOWN": 20,
    "LEFT": 21,
    "RIGHT": 22,
    "OK": 23,
    "BACK": 4,
    "HOME": 3,
    "MENU": 82,
    "SEARCH": 84,
    
    # Media
    "PLAY_PAUSE": 85,
    "STOP": 86,
    "NEXT": 87,
    "PREV": 88,
    "REWIND": 89,
    "FAST_FORWARD": 90,
    
    # Volume & Channels
    "VOL_UP": 24,
    "VOL_DOWN": 25,
    "MUTE": 164,
    "CH_UP": 166,
    "CH_DOWN": 167,
    "INFO": 165,
    "GUIDE": 172,
    "SETTINGS": 176,
    "POWER": 26,
    
    # Numbers
    "0": 7, "1": 8, "2": 9, "3": 10, "4": 11,
    "5": 12, "6": 13, "7": 14, "8": 15, "9": 16,
}


class AdbController:
    def __init__(self, device_ip=DEFAULT_DEVICE):
        self.device_ip = device_ip
        self.cmd_queue = queue.Queue()
        self.is_connected = False
        self.status_callback = None
        self.worker = threading.Thread(target=self._worker_loop, daemon=True)
        self.worker.start()

    def set_device(self, new_ip):
        self.device_ip = new_ip.strip()
        self.connect()

    def connect(self):
        def _bg_connect():
            try:
                subprocess.run([ADB_PATH, "connect", self.device_ip], capture_output=True, text=True, timeout=5)
                # Verify
                res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True, timeout=5)
                self.is_connected = self.device_ip in res.stdout
                if self.status_callback:
                    self.status_callback(self.is_connected, f"Connected to {self.device_ip}" if self.is_connected else f"Offline: {self.device_ip}")
            except Exception as e:
                self.is_connected = False
                if self.status_callback:
                    self.status_callback(False, f"Error: {e}")

        threading.Thread(target=_bg_connect, daemon=True).start()

    def send_key(self, keycode_or_name):
        if isinstance(keycode_or_name, str):
            code = KEYCODES.get(keycode_or_name.upper(), keycode_or_name)
        else:
            code = keycode_or_name
        self.cmd_queue.put(str(code))

    def _worker_loop(self):
        while True:
            code = self.cmd_queue.get()
            try:
                subprocess.run(
                    [ADB_PATH, "-s", self.device_ip, "shell", "input", "keyevent", code],
                    capture_output=True,
                    timeout=2
                )
            except Exception as e:
                print(f"[ADB Error] Failed to send keyevent {code}: {e}")
            finally:
                self.cmd_queue.task_done()


# ==========================================
# GUI IMPLEMENTATION (Tkinter)
# ==========================================
def run_gui(device_ip=DEFAULT_DEVICE):
    import tkinter as tk
    from tkinter import ttk, messagebox

    root = tk.Tk()
    root.title("NTV Virtual TV Remote")
    root.geometry("360x640")
    root.resizable(False, False)
    root.configure(bg="#121316")

    adb = AdbController(device_ip)

    # Styles
    FONT_FAMILY = "Segoe UI"
    BG_DARK = "#121316"
    PANEL_BG = "#1e2025"
    ACCENT_BLUE = "#0078d4"
    ACCENT_HOVER = "#1084d8"
    BTN_BG = "#2a2d34"
    BTN_HOVER = "#363a43"
    TEXT_COLOR = "#ffffff"
    TEXT_MUTED = "#8b949e"

    # Status Bar Frame
    status_frame = tk.Frame(root, bg=PANEL_BG, height=45)
    status_frame.pack(fill=tk.X, padx=10, pady=(10, 5))
    status_frame.pack_propagate(False)

    status_dot = tk.Label(status_frame, text="●", fg="#ef4444", bg=PANEL_BG, font=(FONT_FAMILY, 14, "bold"))
    status_dot.pack(side=tk.LEFT, padx=(10, 4))

    status_label = tk.Label(status_frame, text=f"Connecting: {device_ip}", fg=TEXT_MUTED, bg=PANEL_BG, font=(FONT_FAMILY, 9))
    status_label.pack(side=tk.LEFT, padx=2)

    def on_status_update(connected, msg):
        def _update():
            status_dot.config(fg="#22c55e" if connected else "#ef4444")
            status_label.config(text=msg, fg="#ffffff" if connected else "#f87171")
        root.after(0, _update)

    adb.status_callback = on_status_update
    adb.connect()

    reconnect_btn = tk.Button(
        status_frame, text="🔄", bg=PANEL_BG, fg=TEXT_MUTED, activebackground=BTN_HOVER,
        activeforeground=TEXT_COLOR, bd=0, font=(FONT_FAMILY, 11), cursor="hand2",
        command=adb.connect
    )
    reconnect_btn.pack(side=tk.RIGHT, padx=8)

    # Helper for creating styled buttons
    def make_btn(parent, text, cmd, width=5, height=2, bg=BTN_BG, fg=TEXT_COLOR, font=(FONT_FAMILY, 10, "bold")):
        btn = tk.Button(
            parent, text=text, command=cmd, width=width, height=height,
            bg=bg, fg=fg, activebackground=ACCENT_BLUE, activeforeground="#ffffff",
            bd=0, relief=tk.FLAT, font=font, cursor="hand2"
        )
        btn.bind("<Enter>", lambda e: btn.config(bg=ACCENT_HOVER if bg == ACCENT_BLUE else BTN_HOVER))
        btn.bind("<Leave>", lambda e: btn.config(bg=bg))
        return btn

    def send(key):
        adb.send_key(key)
        feedback_label.config(text=f"Sent: {key} ({KEYCODES.get(key, key)})")

    # Feedback bar
    feedback_label = tk.Label(root, text="Use On-Screen Buttons or PC Keyboard", bg=BG_DARK, fg=TEXT_MUTED, font=(FONT_FAMILY, 8))
    feedback_label.pack(pady=2)

    # --- TOP ROW: POWER / MENU / HOME / BACK ---
    top_nav = tk.Frame(root, bg=BG_DARK)
    top_nav.pack(pady=6)

    make_btn(top_nav, "POWER", lambda: send("POWER"), width=6, height=1, bg="#7f1d1d", fg="#fca5a5", font=(FONT_FAMILY, 8, "bold")).pack(side=tk.LEFT, padx=5)
    make_btn(top_nav, "MENU", lambda: send("MENU"), width=6, height=1, font=(FONT_FAMILY, 8, "bold")).pack(side=tk.LEFT, padx=5)
    make_btn(top_nav, "HOME", lambda: send("HOME"), width=6, height=1, font=(FONT_FAMILY, 8, "bold")).pack(side=tk.LEFT, padx=5)
    make_btn(top_nav, "BACK", lambda: send("BACK"), width=6, height=1, bg="#374151", font=(FONT_FAMILY, 8, "bold")).pack(side=tk.LEFT, padx=5)

    # --- D-PAD SECTION ---
    dpad_container = tk.Frame(root, bg=BG_DARK)
    dpad_container.pack(pady=10)

    # Up
    up_row = tk.Frame(dpad_container, bg=BG_DARK)
    up_row.pack()
    make_btn(up_row, "▲\nUP", lambda: send("UP"), width=8, height=2, font=(FONT_FAMILY, 9, "bold")).pack()

    # Mid (Left, OK, Right)
    mid_row = tk.Frame(dpad_container, bg=BG_DARK)
    mid_row.pack(pady=4)
    make_btn(mid_row, "◀ LEFT", lambda: send("LEFT"), width=8, height=3, font=(FONT_FAMILY, 9, "bold")).pack(side=tk.LEFT, padx=4)
    make_btn(mid_row, "OK", lambda: send("OK"), width=8, height=3, bg=ACCENT_BLUE, fg="#ffffff", font=(FONT_FAMILY, 11, "bold")).pack(side=tk.LEFT, padx=4)
    make_btn(mid_row, "RIGHT ▶", lambda: send("RIGHT"), width=8, height=3, font=(FONT_FAMILY, 9, "bold")).pack(side=tk.LEFT, padx=4)

    # Down
    down_row = tk.Frame(dpad_container, bg=BG_DARK)
    down_row.pack()
    make_btn(down_row, "▼\nDOWN", lambda: send("DOWN"), width=8, height=2, font=(FONT_FAMILY, 9, "bold")).pack()

    # --- CHANNEL & VOLUME SECTION ---
    cv_frame = tk.Frame(root, bg=BG_DARK)
    cv_frame.pack(pady=8)

    # Vol Column
    vol_col = tk.Frame(cv_frame, bg=PANEL_BG, padx=8, pady=6)
    vol_col.pack(side=tk.LEFT, padx=12)
    tk.Label(vol_col, text="VOLUME", bg=PANEL_BG, fg=TEXT_MUTED, font=(FONT_FAMILY, 7, "bold")).pack()
    make_btn(vol_col, "+", lambda: send("VOL_UP"), width=4, height=1, font=(FONT_FAMILY, 10, "bold")).pack(pady=2)
    make_btn(vol_col, "MUTE", lambda: send("MUTE"), width=4, height=1, font=(FONT_FAMILY, 7)).pack(pady=2)
    make_btn(vol_col, "-", lambda: send("VOL_DOWN"), width=4, height=1, font=(FONT_FAMILY, 10, "bold")).pack(pady=2)

    # Media Center Column
    media_col = tk.Frame(cv_frame, bg=BG_DARK)
    media_col.pack(side=tk.LEFT, padx=6)
    make_btn(media_col, "⏮ PREV", lambda: send("PREV"), width=7, height=1, font=(FONT_FAMILY, 7)).pack(pady=2)
    make_btn(media_col, "⏯ PLAY/PAUSE", lambda: send("PLAY_PAUSE"), width=11, height=1, bg="#15803d", fg="#ffffff", font=(FONT_FAMILY, 8, "bold")).pack(pady=3)
    make_btn(media_col, "⏭ NEXT", lambda: send("NEXT"), width=7, height=1, font=(FONT_FAMILY, 7)).pack(pady=2)

    # Channel Column
    ch_col = tk.Frame(cv_frame, bg=PANEL_BG, padx=8, pady=6)
    ch_col.pack(side=tk.LEFT, padx=12)
    tk.Label(ch_col, text="CHANNEL", bg=PANEL_BG, fg=TEXT_MUTED, font=(FONT_FAMILY, 7, "bold")).pack()
    make_btn(ch_col, "CH +", lambda: send("CH_UP"), width=4, height=1, font=(FONT_FAMILY, 8, "bold")).pack(pady=2)
    make_btn(ch_col, "INFO", lambda: send("INFO"), width=4, height=1, font=(FONT_FAMILY, 7)).pack(pady=2)
    make_btn(ch_col, "CH -", lambda: send("CH_DOWN"), width=4, height=1, font=(FONT_FAMILY, 8, "bold")).pack(pady=2)

    # --- NUMBER PAD TOGGLE / SECTION ---
    num_frame = tk.Frame(root, bg=BG_DARK)
    num_frame.pack(pady=5)

    num_rows = [
        ["1", "2", "3"],
        ["4", "5", "6"],
        ["7", "8", "9"],
        ["GUIDE", "0", "SEARCH"]
    ]
    for row in num_rows:
        rf = tk.Frame(num_frame, bg=BG_DARK)
        rf.pack(pady=1)
        for num in row:
            if num in ["GUIDE", "SEARCH"]:
                make_btn(rf, num, lambda k=num: send(k), width=6, height=1, font=(FONT_FAMILY, 7)).pack(side=tk.LEFT, padx=2)
            else:
                make_btn(rf, num, lambda k=num: send(k), width=6, height=1, font=(FONT_FAMILY, 8)).pack(side=tk.LEFT, padx=2)

    # ==========================================
    # KEYBOARD SHORTCUT BINDINGS
    # ==========================================
    def on_key_press(event):
        sym = event.keysym
        char = event.char
        key_map = {
            "Up": "UP",
            "Down": "DOWN",
            "Left": "LEFT",
            "Right": "RIGHT",
            "Return": "OK",
            "space": "OK",
            "BackSpace": "BACK",
            "Escape": "BACK",
            "Home": "HOME",
            "Prior": "CH_UP",     # PageUp
            "Next": "CH_DOWN",     # PageDown
        }
        if sym in key_map:
            send(key_map[sym])
        elif char.lower() == "w":
            send("UP")
        elif char.lower() == "s":
            send("DOWN")
        elif char.lower() == "a":
            send("LEFT")
        elif char.lower() == "d":
            send("RIGHT")
        elif char.lower() == "m":
            send("MENU")
        elif char.lower() == "p":
            send("PLAY_PAUSE")
        elif char.lower() == "i":
            send("INFO")
        elif char in "0123456789":
            send(char)

    root.bind("<KeyPress>", on_key_press)
    root.focus_set()

    root.mainloop()


# ==========================================
# CLI IMPLEMENTATION (Terminal)
# ==========================================
def run_cli(device_ip=DEFAULT_DEVICE):
    import msvcrt

    adb = AdbController(device_ip)
    adb.connect()
    print("=" * 55)
    print("           NTV VIRTUAL TV REMOTE (CLI MODE)          ")
    print(f" Target Device: {device_ip}")
    print("=" * 55)
    print(" Controls:")
    print("  Arrow Keys / W,A,S,D : D-pad Navigation")
    print("  Enter / Space        : OK (Center)")
    print("  Backspace / Esc      : Back")
    print("  M                    : Menu")
    print("  H                    : Home")
    print("  P                    : Play / Pause")
    print("  + / -                : Volume Up / Down")
    print("  U / J (PageUp/Down)  : Channel Up / Down")
    print("  0 - 9                : Number keys")
    print("  Q                    : Quit")
    print("=" * 55)
    print("Listening for keypresses (Direct - no Enter needed)...")

    while True:
        try:
            ch = msvcrt.getch()
            # Handle special arrow keys on Windows (which prefix with \xe0 or \x00)
            if ch in [b'\x00', b'\xe0']:
                ch2 = msvcrt.getch()
                if ch2 == b'H':
                    print("-> [UP]")
                    adb.send_key("UP")
                elif ch2 == b'P':
                    print("-> [DOWN]")
                    adb.send_key("DOWN")
                elif ch2 == b'K':
                    print("-> [LEFT]")
                    adb.send_key("LEFT")
                elif ch2 == b'M':
                    print("-> [RIGHT]")
                    adb.send_key("RIGHT")
                elif ch2 == b'I': # PageUp
                    print("-> [CH UP]")
                    adb.send_key("CH_UP")
                elif ch2 == b'Q': # PageDown
                    print("-> [CH DOWN]")
                    adb.send_key("CH_DOWN")
                continue

            char = ch.decode("utf-8", errors="ignore").lower()

            if char == 'q':
                print("Exiting Remote...")
                break
            elif ch in [b'\r', b'\n', b' ']:
                print("-> [OK]")
                adb.send_key("OK")
            elif ch in [b'\x08', b'\x1b', b'b']:
                print("-> [BACK]")
                adb.send_key("BACK")
            elif char == 'w':
                print("-> [UP]")
                adb.send_key("UP")
            elif char == 's':
                print("-> [DOWN]")
                adb.send_key("DOWN")
            elif char == 'a':
                print("-> [LEFT]")
                adb.send_key("LEFT")
            elif char == 'd':
                print("-> [RIGHT]")
                adb.send_key("RIGHT")
            elif char == 'm':
                print("-> [MENU]")
                adb.send_key("MENU")
            elif char == 'h':
                print("-> [HOME]")
                adb.send_key("HOME")
            elif char == 'p':
                print("-> [PLAY/PAUSE]")
                adb.send_key("PLAY_PAUSE")
            elif char == 'u' or char == '+':
                print("-> [CH UP]")
                adb.send_key("CH_UP")
            elif char == 'j' or char == '-':
                print("-> [CH DOWN]")
                adb.send_key("CH_DOWN")
            elif char == 'i':
                print("-> [INFO]")
                adb.send_key("INFO")
            elif char in "0123456789":
                print(f"-> [NUM {char}]")
                adb.send_key(char)

        except KeyboardInterrupt:
            break


if __name__ == "__main__":
    target = DEFAULT_DEVICE
    if len(sys.argv) > 1 and not sys.argv[1].startswith("--"):
        target = sys.argv[1]

    if "--cli" in sys.argv:
        run_cli(target)
    else:
        try:
            run_gui(target)
        except Exception as e:
            print(f"GUI failed to initialize ({e}). Falling back to CLI mode...")
            run_cli(target)
