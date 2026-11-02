import mysql.connector

conn = mysql.connector.connect(
    host="localhost",
    user="root",
    password="shri7798",
    database="ambulance_db"
)
cur = conn.cursor()
try:
    cur.execute("ALTER TABLE system_logs MODIFY COLUMN log_level VARCHAR(50)")
    cur.execute("ALTER TABLE system_logs MODIFY COLUMN component VARCHAR(100)")
    conn.commit()
    print("SUCCESS: system_logs table altered successfully.")
except Exception as e:
    print("Notice:", e)
finally:
    cur.close()
    conn.close()

