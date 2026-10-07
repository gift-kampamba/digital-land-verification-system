-- One-time GPS backfill for existing parcels.
-- Run this in MySQL Workbench against the land_verification database.

USE land_verification;

ALTER TABLE land_parcel
    ADD COLUMN IF NOT EXISTS gps_lat VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS gps_lng VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS document_hash VARCHAR(128) NULL;

-- Example: populate coordinates for a known parcel.
-- Replace the values below with the actual coordinates for the parcel.
-- UPDATE land_parcel
-- SET gps_lat = '-15.4167',
--     gps_lng = '28.2833'
-- WHERE parcel_number = 'ZM-LUS-2026-0001';

-- If you already have coordinates in a separate source/table, map them here.
-- UPDATE land_parcel p
-- JOIN your_source_table s ON s.parcel_number = p.parcel_number
-- SET p.gps_lat = s.gps_lat,
--     p.gps_lng = s.gps_lng
-- WHERE p.gps_lat IS NULL OR p.gps_lng IS NULL;

-- Optional cleanup for empty strings.
UPDATE land_parcel
SET gps_lat = NULLIF(TRIM(gps_lat), ''),
    gps_lng = NULLIF(TRIM(gps_lng), '')
WHERE gps_lat IS NOT NULL OR gps_lng IS NOT NULL;
