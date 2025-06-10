import serial
import time

try:
    # Adjust the port and baud rate to match your Arduino setup
    ser = serial.Serial('/dev/rfcomm0', 9600, timeout=1)
    print("Connected to HC-05")

    while True:
        if ser.in_waiting:
            data = ser.readline().decode('utf-8').strip()
            if data:
                print("Received:", data)
        time.sleep(0.1)

except serial.SerialException as e:
    print(f"Serial connection error: {e}")

except KeyboardInterrupt:
    print("\nDisconnected")
    ser.close()
