import serial
import time

try:
    start_time = time.time()
    last_data_time = time.time()

    ser = serial.Serial('/dev/rfcomm0', 9600, timeout=1)
    print("Connected to DriveEyePro_V2")

    while True:
        try:
            if ser.in_waiting:
                data = ser.readline().decode('utf-8').strip()
                if data:
                    #print("Received:", data)
                    last_data_time = time.time()  # Reset timer on successful data reception

            # Check for timeout (no data received for more than 2 seconds)
            if time.time() - last_data_time > 2:
                raise TimeoutError("No data received for 2 seconds.")

            time.sleep(0.1)

        except TimeoutError as e:
            uptime_seconds = time.time() - start_time
            hours, remainder = divmod(int(uptime_seconds), 3600)
            minutes, seconds = divmod(remainder, 60)
            print(f"\n{e}")
            print(f"Uptime before disconnection: {hours}h {minutes}m {seconds}s")
            ser.close()
            break

except serial.SerialException as e:
    print(f"Serial connection error: {e}")

except KeyboardInterrupt:
    uptime_seconds = time.time() - start_time
    hours, remainder = divmod(int(uptime_seconds), 3600)
    minutes, seconds = divmod(remainder, 60)
    print("\nDisconnected by user")
    print(f"Uptime before disconnection: {hours}h {minutes}m {seconds}s")
    ser.close()
