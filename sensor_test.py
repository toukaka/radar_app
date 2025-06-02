import bluetooth
import tkinter as tk
from tkinter import ttk
import threading
import time

# Replace with your Android device MAC address
android_mac_address = "4C:6A:F6:37:4D:EE"
uuid = "00001101-0000-1000-8000-00805F9B34FB"

sock = None
connected = False

# Tkinter root
root = tk.Tk()
root.title("Bluetooth Sensor Simulator")

# Status label
status_label = ttk.Label(root, text="Not connected.")
status_label.grid(row=3, column=0)

# --- Bluetooth connection thread ---
def bluetooth_auto_connect():
    global sock, connected
    while not connected:
        try:
            status_label.config(text="Searching for Bluetooth service...")
            service_matches = bluetooth.find_service(uuid=uuid, address=android_mac_address)

            if not service_matches:
                status_label.config(text="Service not found. Retrying...")
                time.sleep(1)
                continue

            match = service_matches[0]
            port = match["port"]
            host = match["host"]

            sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            sock.connect((host, port))
            connected = True
            status_label.config(text="Connected to device.")
            send_button.config(state="normal")
        except Exception as e:
            if sock:
                try:
                    sock.close()
                except:
                    pass
                sock = None
            connected = False
            status_label.config(text=f"Retrying in 1s... ({e})")
            send_button.config(state="disabled")
            time.sleep(1)

# Start auto-connection in background
threading.Thread(target=bluetooth_auto_connect, daemon=True).start()

# --- Tkinter GUI ---
frame = ttk.Frame(root, padding=10)
frame.grid(row=1, column=0)

def create_vertical_slider(label_text, row, col):
    col_frame = ttk.Frame(frame)
    col_frame.grid(row=row, column=col, padx=15)

    value_var = tk.IntVar(value=50)

    value_label = ttk.Label(col_frame, textvariable=value_var, font=("Arial", 12, "bold"))
    value_label.pack()

    label = ttk.Label(col_frame, text=label_text)
    label.pack()

    scale = ttk.Scale(
        col_frame,
        from_=100,
        to=0,
        orient="vertical",
        length=200,
        command=lambda val: value_var.set(int(float(val)))
    )
    scale.set(50)
    scale.pack()

    return scale

scale_front = create_vertical_slider("Front", 0, 0)
scale_back = create_vertical_slider("Back", 0, 1)
scale_left = create_vertical_slider("Left", 0, 2)
scale_right = create_vertical_slider("Right", 0, 3)

def send_values():
    global sock, connected
    if not connected or sock is None:
        status_label.config(text="Not connected.")
        return

    values = [
        int(scale_front.get()),
        int(scale_back.get()),
        int(scale_left.get()),
        int(scale_right.get())
    ]
    msg = ",".join(map(str, values)) + "\n"
    try:
        sock.send(msg.encode())
        status_label.config(text=f"Sent: {msg.strip()}")
    except Exception as e:
        status_label.config(text=f"Send failed: {e}")
        connected = False
        send_button.config(state="disabled")
        try:
            sock.close()
        except:
            pass
        sock = None
        # Restart connection thread
        threading.Thread(target=bluetooth_auto_connect, daemon=True).start()

send_button = ttk.Button(root, text="Send", command=send_values, state="disabled")
send_button.grid(row=2, column=0, pady=10)

def on_closing():
    global sock
    try:
        if sock:
            sock.close()
    except:
        pass
    root.destroy()

root.protocol("WM_DELETE_WINDOW", on_closing)
root.mainloop()
