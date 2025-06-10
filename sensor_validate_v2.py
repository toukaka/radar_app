import tkinter as tk
import ttkbootstrap as ttkb
from ttkbootstrap.widgets import Meter
import serial
import time
import threading

SERIAL_PORT = '/dev/rfcomm0'  # Adjust as needed
BAUD_RATE = 9600

class SerialMonitorGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("DriveEyePro Speedometers")
        self.start_time = time.time()
        self.values = [0, 0, 0, 0]
        self.meters = []

        self.style = ttkb.Style('flatly')
        self.create_widgets()

        self.running = True
        threading.Thread(target=self.read_serial, daemon=True).start()
        self.update_gui()

    def create_widgets(self):
        frame = tk.Frame(self.root)
        frame.pack(pady=20)

        for i in range(4):
            meter = Meter(
                frame,
                metersize=150,
                meterthickness=15,
                amounttotal=100,
                amountused=0,
                metertype='full',
                textright='%',
                textleft='',        # optional prefix
                textfont='Helvetica 18 bold',
                bootstyle='success'
            )
            meter.grid(row=0, column=i, padx=10)
            self.meters.append(meter)

        self.uptime_label = tk.Label(self.root, text="Uptime: 0m 0s", font=("Arial", 12))
        self.uptime_label.pack(pady=10)

    def read_serial(self):
        try:
            with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1) as ser:
                while self.running:
                    if ser.in_waiting:
                        data = ser.readline().decode('utf-8').strip()
                        parts = data.split(',')
                        if len(parts) == 4:
                            try:
                                self.values = [int(p) for p in parts]
                            except ValueError:
                                print("Ignoring invalid:", data)
                    time.sleep(0.1)
        except serial.SerialException as e:
            print("Serial error:", e)

    def update_gui(self):
        for i, meter in enumerate(self.meters):
            val = self.values[i]
            meter.configure(amountused=val)
            # Color logic
            if val <= 40:
                meter.configure(bootstyle='success')
            elif val <= 80:
                meter.configure(bootstyle='warning')
            else:
                meter.configure(bootstyle='danger')

        elapsed = int(time.time() - self.start_time)
        m, s = divmod(elapsed, 60)
        self.uptime_label.config(text=f"Uptime: {m}m {s}s")

        self.root.after(200, self.update_gui)

    def stop(self):
        self.running = False

if __name__ == "__main__":
    root = tk.Tk()
    app = SerialMonitorGUI(root)
    try:
        root.mainloop()
    finally:
        app.stop()
