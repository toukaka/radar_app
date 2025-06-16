import serial
import time
import random

SERIAL_PORT = '/dev/rfcomm0'
BAUD_RATE = 9600

while True:
    try:
        ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
        print("Connected to Bluetooth device.")

        while True:
            x = random.randint(0, 100)
            y = random.randint(0, 100)
            z = random.randint(0, 100)
            w = random.randint(0, 100)

            message = f"{x},{y},{z},{w}\n"
            ser.write(message.encode('utf-8'))
            print(f"Sent: {message.strip()}")

            time.sleep(0.5)

    except (serial.SerialException, OSError) as e:
        print(f"\nConnection error: {e}")
        print("Reconnecting in 2 seconds...")
        try:
            ser.close()
        except:
            pass
        time.sleep(2)
        print("Attempting to reconnect...")

    except KeyboardInterrupt:
        try:
            ser.close()
        except:
            pass
        print("\nStopped by user.")
        break
