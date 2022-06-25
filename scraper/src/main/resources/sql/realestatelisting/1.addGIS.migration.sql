-- Adds a column containing a long/lat point in GPS (lat/long) format.
ALTER TABLE real_estate_listings
    ADD COLUMN geom GEOMETRY(Point, 4326);