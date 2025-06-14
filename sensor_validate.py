import serial
import time

SERIAL_PORT = '/dev/rfcomm0'
BAUD_RATE = 9600

while True:
    try:
        ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
        print("Connected to DriveEyePro_V2")
        start_time = time.time()
        last_data_time = time.time()

        while True:
            if ser.in_waiting:
                data = ser.readline().decode('utf-8').strip()
                print(data)
                if data:
                    last_data_time = time.time()  # Reset timer on successful data reception

            # Check for timeout (no data received for more than 2 seconds)
            if time.time() - last_data_time > 2:
                raise TimeoutError("No data received for 2 seconds.")

            time.sleep(0.1)

    except (serial.SerialException, TimeoutError) as e:
        uptime_seconds = time.time() - start_time
        hours, remainder = divmod(int(uptime_seconds), 3600)
        minutes, seconds = divmod(remainder, 60)
        print(f"\n{e}")
        print(f"Uptime before disconnection: {hours}h {minutes}m {seconds}s")

        try:
            ser.close()
        except:
            pass

        print("Reconnecting in 2 seconds...")
        time.sleep(2)
        print("Attempting to reconnect...")
