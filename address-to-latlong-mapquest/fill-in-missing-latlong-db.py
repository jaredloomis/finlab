import psycopg2
import subprocess

conn = psycopg2.connect(database = "market_analysis", user = "postgres", password = "", host = "127.0.0.1", port = "5432")
conn.autocommit = True
cur = conn.cursor()
print("Opened database successfully")

# Get list of addresses, along with associated row id
cur.execute("SELECT id, address FROM real_estate_listings WHERE geom IS NULL;")
rows = cur.fetchall()

# For each row, get the lat&long, update row
for (rowid, address) in rows:
    cmd = [
        "/bin/bash",
        "../address-to-latlong-mapquest/address-to-latlong.sh",
        "\"" + address + "\""
    ]
    output = subprocess.run(cmd, check=True, stdout=subprocess.PIPE).stdout
    lat, long = output.split(b",")
    print(address, "| latlong", lat, long)
    # Execute update
    # XXX NOTE long, lat flipped order, used by PostGIS
    # 3857 = Web Mercator SRID
    cur.execute(
        "UPDATE real_estate_listings SET geom = ST_SetSRID(ST_MakePoint(%s, %s), 4326) WHERE id = %s;",
        (float(long.strip()), float(lat.strip()), rowid)
    )
conn.close()

