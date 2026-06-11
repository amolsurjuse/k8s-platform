\set ON_ERROR_STOP on

\if :{?demo_user_id}
\else
\set demo_user_id '0322b0cc-5419-41b1-bd99-c1be65ab8004'
\endif

\echo 'Loading ElectraHub demo charger inventory into charger_management_db'
\connect charger_management_db
BEGIN;

INSERT INTO enterprises (
  enterprise_id, name, country_code, party_id, timezone, enabled, created_at, updated_at, icon, icon_url
) VALUES
  ('ENT-US-DEV', 'Electra Hub Mobility Inc.', 'US', 'EHB', 'America/New_York', true, current_timestamp, current_timestamp, 'electrahub-enterprise', '/electra-hub-icon.svg'),
  ('ENT-EU-DEV', 'Electra Hub Europe BV', 'NL', 'EHE', 'Europe/Amsterdam', true, current_timestamp, current_timestamp, 'electrahub-enterprise', '/electra-hub-icon.svg')
ON CONFLICT (enterprise_id) DO UPDATE
   SET name = excluded.name,
       country_code = excluded.country_code,
       party_id = excluded.party_id,
       timezone = excluded.timezone,
       enabled = excluded.enabled,
       icon = excluded.icon,
       icon_url = excluded.icon_url,
       updated_at = current_timestamp;

INSERT INTO networks (
  network_id, enterprise_id, name, region, operator_email, enabled, created_at, updated_at, icon, icon_url
) VALUES
  ('NW-EH-USA-DEMO', 'ENT-US-DEV', 'ElectraHub USA Corridor', 'USA', 'ops-us@electrahub.com', true, current_timestamp, current_timestamp, 'electrahub-network', '/electra-hub-icon.svg'),
  ('NW-EH-EUR-DEMO', 'ENT-EU-DEV', 'ElectraHub Europe Corridor', 'EUROPE', 'ops-eu@electrahub.com', true, current_timestamp, current_timestamp, 'electrahub-network', '/electra-hub-icon.svg')
ON CONFLICT (network_id) DO UPDATE
   SET enterprise_id = excluded.enterprise_id,
       name = excluded.name,
       region = excluded.region,
       operator_email = excluded.operator_email,
       enabled = excluded.enabled,
       icon = excluded.icon,
       icon_url = excluded.icon_url,
       updated_at = current_timestamp;

WITH city_templates(city_index, city_name, state_code, latitude, longitude) AS (
  VALUES
    (1, 'New York', 'NY', 40.7484000, -73.9857000),
    (2, 'Boston', 'MA', 42.3534000, -71.0446000),
    (3, 'Chicago', 'IL', 41.8789000, -87.6359000),
    (4, 'Austin', 'TX', 30.4039000, -97.7254000),
    (5, 'Denver', 'CO', 39.7528000, -104.9999000),
    (6, 'Seattle', 'WA', 47.6224000, -122.3372000),
    (7, 'Los Angeles', 'CA', 34.0440000, -118.2348000),
    (8, 'Miami', 'FL', 25.7663000, -80.1911000),
    (9, 'Phoenix', 'AZ', 33.4484000, -112.0740000),
    (10, 'Portland', 'OR', 45.5152000, -122.6784000),
    (11, 'San Diego', 'CA', 32.7157000, -117.1611000),
    (12, 'Dallas', 'TX', 32.7767000, -96.7970000),
    (13, 'Atlanta', 'GA', 33.7490000, -84.3880000),
    (14, 'Nashville', 'TN', 36.1627000, -86.7816000),
    (15, 'Charlotte', 'NC', 35.2271000, -80.8431000),
    (16, 'Minneapolis', 'MN', 44.9778000, -93.2650000),
    (17, 'Las Vegas', 'NV', 36.1699000, -115.1398000),
    (18, 'Salt Lake City', 'UT', 40.7608000, -111.8910000),
    (19, 'Orlando', 'FL', 28.5383000, -81.3792000),
    (20, 'Philadelphia', 'PA', 39.9526000, -75.1652000),
    (21, 'San Jose', 'CA', 37.3382000, -121.8863000),
    (22, 'Sacramento', 'CA', 38.5816000, -121.4944000),
    (23, 'Houston', 'TX', 29.7604000, -95.3698000),
    (24, 'San Antonio', 'TX', 29.4241000, -98.4936000),
    (25, 'Raleigh', 'NC', 35.7796000, -78.6382000),
    (26, 'Columbus', 'OH', 39.9612000, -82.9988000),
    (27, 'Detroit', 'MI', 42.3314000, -83.0458000),
    (28, 'Indianapolis', 'IN', 39.7684000, -86.1581000),
    (29, 'Kansas City', 'MO', 39.0997000, -94.5786000),
    (30, 'Omaha', 'NE', 41.2565000, -95.9345000),
    (31, 'St. Louis', 'MO', 38.6270000, -90.1994000),
    (32, 'Tampa', 'FL', 27.9506000, -82.4572000),
    (33, 'Jacksonville', 'FL', 30.3322000, -81.6557000),
    (34, 'Pittsburgh', 'PA', 40.4406000, -79.9959000),
    (35, 'Baltimore', 'MD', 39.2904000, -76.6122000),
    (36, 'Washington', 'DC', 38.9072000, -77.0369000),
    (37, 'Albuquerque', 'NM', 35.0844000, -106.6504000),
    (38, 'Boise', 'ID', 43.6150000, -116.2023000),
    (39, 'Spokane', 'WA', 47.6588000, -117.4260000),
    (40, 'Boulder', 'CO', 40.0150000, -105.2705000)
), generated_locations AS (
  SELECT n AS site_number,
         ((n - 1) % 40) + 1 AS city_index,
         ((n - 1) / 40) AS ring_index
  FROM generate_series(1, 800) AS n
)
INSERT INTO locations (
  location_id, network_id, name, city, address, ocpi_location_id,
  enabled, created_at, updated_at, latitude, longitude, icon, icon_url
)
SELECT
  'LOC-USA-' || lpad(g.site_number::text, 3, '0'),
  'NW-EH-USA-DEMO',
  'ElectraHub ' || c.city_name || ' Unique Site ' || lpad(g.site_number::text, 3, '0'),
  c.city_name,
  (1000 + g.site_number)::text || ' ElectraHub Way, ' || c.city_name || ', ' || c.state_code,
  'US*EHB*LOC*USA' || lpad(g.site_number::text, 3, '0'),
  true,
  current_timestamp,
  current_timestamp,
  c.latitude + (g.ring_index * 0.018) + (((g.site_number - 1) % 7) * 0.0017),
  c.longitude + (g.ring_index * 0.018) - (((g.site_number - 1) % 7) * 0.0017),
  'electrahub-location',
  '/electra-hub-icon.svg'
FROM generated_locations g
JOIN city_templates c ON c.city_index = g.city_index
ON CONFLICT (location_id) DO UPDATE
   SET network_id = excluded.network_id,
       name = excluded.name,
       city = excluded.city,
       address = excluded.address,
       ocpi_location_id = excluded.ocpi_location_id,
       enabled = excluded.enabled,
       latitude = excluded.latitude,
       longitude = excluded.longitude,
       icon = excluded.icon,
       icon_url = excluded.icon_url,
       updated_at = current_timestamp;

WITH city_templates(city_index, city_name, country_code, latitude, longitude) AS (
  VALUES
    (1, 'Amsterdam', 'NL', 52.3370000, 4.8736000),
    (2, 'Berlin', 'DE', 52.5219000, 13.4132000),
    (3, 'Paris', 'FR', 48.8924000, 2.2369000),
    (4, 'Madrid', 'ES', 40.4066000, -3.6892000),
    (5, 'Dublin', 'IE', 53.3498000, -6.2603000),
    (6, 'Brussels', 'BE', 50.8503000, 4.3517000),
    (7, 'Milan', 'IT', 45.4642000, 9.1900000),
    (8, 'Vienna', 'AT', 48.2082000, 16.3738000),
    (9, 'Zurich', 'CH', 47.3769000, 8.5417000),
    (10, 'Stockholm', 'SE', 59.3293000, 18.0686000),
    (11, 'Copenhagen', 'DK', 55.6761000, 12.5683000),
    (12, 'Oslo', 'NO', 59.9139000, 10.7522000),
    (13, 'Helsinki', 'FI', 60.1699000, 24.9384000),
    (14, 'Lisbon', 'PT', 38.7223000, -9.1393000),
    (15, 'Prague', 'CZ', 50.0755000, 14.4378000),
    (16, 'Warsaw', 'PL', 52.2297000, 21.0122000),
    (17, 'Munich', 'DE', 48.1351000, 11.5820000),
    (18, 'Hamburg', 'DE', 53.5511000, 9.9937000),
    (19, 'Lyon', 'FR', 45.7640000, 4.8357000),
    (20, 'Barcelona', 'ES', 41.3851000, 2.1734000)
), generated_locations AS (
  SELECT n AS site_number,
         ((n - 1) % 20) + 1 AS city_index,
         ((n - 1) / 20) AS ring_index
  FROM generate_series(1, 200) AS n
)
INSERT INTO locations (
  location_id, network_id, name, city, address, ocpi_location_id,
  enabled, created_at, updated_at, latitude, longitude, icon, icon_url
)
SELECT
  'LOC-EUR-' || lpad(g.site_number::text, 3, '0'),
  'NW-EH-EUR-DEMO',
  'ElectraHub ' || c.city_name || ' Unique Site ' || lpad(g.site_number::text, 3, '0'),
  c.city_name,
  (200 + g.site_number)::text || ' ElectraHub Avenue, ' || c.city_name,
  c.country_code || '*EHB*LOC*EUR' || lpad(g.site_number::text, 3, '0'),
  true,
  current_timestamp,
  current_timestamp,
  c.latitude + (g.ring_index * 0.014) + (((g.site_number - 1) % 5) * 0.0013),
  c.longitude + (g.ring_index * 0.014) - (((g.site_number - 1) % 5) * 0.0013),
  'electrahub-location',
  '/electra-hub-icon.svg'
FROM generated_locations g
JOIN city_templates c ON c.city_index = g.city_index
ON CONFLICT (location_id) DO UPDATE
   SET network_id = excluded.network_id,
       name = excluded.name,
       city = excluded.city,
       address = excluded.address,
       ocpi_location_id = excluded.ocpi_location_id,
       enabled = excluded.enabled,
       latitude = excluded.latitude,
       longitude = excluded.longitude,
       icon = excluded.icon,
       icon_url = excluded.icon_url,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 800) AS n)
INSERT INTO charger_inventory (
  charger_id, display_name, location_id, model, ocpp_version, max_power_kw,
  enabled, created_at, updated_at, icon, icon_url
)
SELECT
  'EH-US-CHG-' || lpad(n::text, 4, '0'),
  'ElectraHub US Site ' || lpad(n::text, 3, '0') || ' Charger 1',
  'LOC-USA-' || lpad(n::text, 3, '0'),
  CASE WHEN n % 5 = 0 THEN 'EH-Ultra-350' WHEN n % 3 = 0 THEN 'EH-DC-250' ELSE 'EH-DC-150' END,
  CASE WHEN n % 7 = 0 THEN 'OCPP201' ELSE 'OCPP16J' END,
  CASE WHEN n % 5 = 0 THEN 350.00 WHEN n % 3 = 0 THEN 250.00 ELSE 150.00 END,
  true,
  current_timestamp,
  current_timestamp,
  'electrahub-enterprise',
  '/electra-hub-icon.svg'
FROM numbered
ON CONFLICT (charger_id) DO UPDATE
   SET display_name = excluded.display_name,
       location_id = excluded.location_id,
       model = excluded.model,
       ocpp_version = excluded.ocpp_version,
       max_power_kw = excluded.max_power_kw,
       enabled = excluded.enabled,
       icon = excluded.icon,
       icon_url = excluded.icon_url,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 200) AS n)
INSERT INTO charger_inventory (
  charger_id, display_name, location_id, model, ocpp_version, max_power_kw,
  enabled, created_at, updated_at, icon, icon_url
)
SELECT
  'EH-EU-CHG-' || lpad(n::text, 4, '0'),
  'ElectraHub EU Site ' || lpad(n::text, 3, '0') || ' Charger 1',
  'LOC-EUR-' || lpad(n::text, 3, '0'),
  CASE WHEN n % 4 = 0 THEN 'EH-AC-22' WHEN n % 3 = 0 THEN 'EH-HPC-300' ELSE 'EH-DC-150' END,
  CASE WHEN n % 6 = 0 THEN 'OCPP201' ELSE 'OCPP16J' END,
  CASE WHEN n % 4 = 0 THEN 22.00 WHEN n % 3 = 0 THEN 300.00 ELSE 150.00 END,
  true,
  current_timestamp,
  current_timestamp,
  'electrahub-enterprise',
  '/electra-hub-icon.svg'
FROM numbered
ON CONFLICT (charger_id) DO UPDATE
   SET display_name = excluded.display_name,
       location_id = excluded.location_id,
       model = excluded.model,
       ocpp_version = excluded.ocpp_version,
       max_power_kw = excluded.max_power_kw,
       enabled = excluded.enabled,
       icon = excluded.icon,
       icon_url = excluded.icon_url,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 800) AS n)
INSERT INTO evse_inventory (
  evse_id, charger_id, evse_uid, zone, capabilities, enabled, created_at, updated_at
)
SELECT
  'EVSE-US-' || lpad(n::text, 4, '0'),
  'EH-US-CHG-' || lpad(n::text, 4, '0'),
  'US*EHB*E*US' || lpad(n::text, 4, '0'),
  'US-DEMO-' || lpad((((n - 1) % 40) + 1)::text, 2, '0'),
  'REMOTE_START_STOP,METER_VALUES,RFID,PLUG_AND_CHARGE',
  true,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (evse_id) DO UPDATE
   SET charger_id = excluded.charger_id,
       evse_uid = excluded.evse_uid,
       zone = excluded.zone,
       capabilities = excluded.capabilities,
       enabled = excluded.enabled,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 200) AS n)
INSERT INTO evse_inventory (
  evse_id, charger_id, evse_uid, zone, capabilities, enabled, created_at, updated_at
)
SELECT
  'EVSE-EU-' || lpad(n::text, 4, '0'),
  'EH-EU-CHG-' || lpad(n::text, 4, '0'),
  'EU*EHB*E*EU' || lpad(n::text, 4, '0'),
  'EU-DEMO-' || lpad((((n - 1) % 20) + 1)::text, 2, '0'),
  'REMOTE_START_STOP,METER_VALUES,RFID,PLUG_AND_CHARGE',
  true,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (evse_id) DO UPDATE
   SET charger_id = excluded.charger_id,
       evse_uid = excluded.evse_uid,
       zone = excluded.zone,
       capabilities = excluded.capabilities,
       enabled = excluded.enabled,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 800) AS n)
INSERT INTO connector_inventory (
  connector_id, evse_id, standard, format, power_type, max_power_kw,
  ocpi_tariff_ids, enabled, created_at, updated_at
)
SELECT
  'CON-US-' || lpad(n::text, 4, '0'),
  'EVSE-US-' || lpad(n::text, 4, '0'),
  CASE WHEN n % 7 = 0 THEN 'CHADEMO' WHEN n % 5 = 0 THEN 'NACS' ELSE 'CCS1' END,
  'CABLE',
  'DC',
  CASE WHEN n % 5 = 0 THEN 350.00 WHEN n % 7 = 0 THEN 62.50 WHEN n % 3 = 0 THEN 250.00 ELSE 150.00 END,
  '9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'),
  true,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (connector_id) DO UPDATE
   SET evse_id = excluded.evse_id,
       standard = excluded.standard,
       format = excluded.format,
       power_type = excluded.power_type,
       max_power_kw = excluded.max_power_kw,
       ocpi_tariff_ids = excluded.ocpi_tariff_ids,
       enabled = excluded.enabled,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 200) AS n)
INSERT INTO connector_inventory (
  connector_id, evse_id, standard, format, power_type, max_power_kw,
  ocpi_tariff_ids, enabled, created_at, updated_at
)
SELECT
  'CON-EU-' || lpad(n::text, 4, '0'),
  'EVSE-EU-' || lpad(n::text, 4, '0'),
  CASE WHEN n % 4 = 0 THEN 'IEC_62196_T2' ELSE 'IEC_62196_T2_COMBO' END,
  'CABLE',
  CASE WHEN n % 4 = 0 THEN 'AC_3_PHASE' ELSE 'DC' END,
  CASE WHEN n % 4 = 0 THEN 22.00 WHEN n % 3 = 0 THEN 300.00 ELSE 150.00 END,
  '9f000000-0000-0000-0000-' || lpad((n + 800)::text, 12, '0'),
  true,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (connector_id) DO UPDATE
   SET evse_id = excluded.evse_id,
       standard = excluded.standard,
       format = excluded.format,
       power_type = excluded.power_type,
       max_power_kw = excluded.max_power_kw,
       ocpi_tariff_ids = excluded.ocpi_tariff_ids,
       enabled = excluded.enabled,
       updated_at = current_timestamp;

COMMIT;

\echo 'Loading ElectraHub demo pricing into pricing_db'
\connect pricing_db
BEGIN;

WITH numbered AS (SELECT n FROM generate_series(1, 1000) AS n)
INSERT INTO pricing_plans (
  id, name, description, currency, pricing_type, connector_type, active,
  valid_from, created_at, updated_at
)
SELECT
  ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  CASE
    WHEN n <= 800 THEN 'ElectraHub USA Demo Tariff ' || lpad(n::text, 3, '0')
    ELSE 'ElectraHub Europe Demo Tariff ' || lpad((n - 800)::text, 3, '0')
  END,
  CASE
    WHEN n <= 800 THEN 'USA demo pricing plan for seeded connector CON-US-' || lpad(n::text, 4, '0')
    ELSE 'Europe demo pricing plan for seeded connector CON-EU-' || lpad((n - 800)::text, 4, '0')
  END,
  CASE WHEN n <= 800 THEN 'USD' ELSE 'EUR' END,
  'FLAT',
  CASE
    WHEN n <= 800 AND n % 7 = 0 THEN 'CHADEMO'
    WHEN n <= 800 AND n % 5 = 0 THEN 'NACS'
    WHEN n <= 800 THEN 'CCS1'
    WHEN (n - 800) % 4 = 0 THEN 'IEC_62196_T2'
    ELSE 'IEC_62196_T2_COMBO'
  END,
  true,
  timestamp with time zone '2026-01-01 00:00:00+00',
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (id) DO UPDATE
   SET name = excluded.name,
       description = excluded.description,
       currency = excluded.currency,
       pricing_type = excluded.pricing_type,
       connector_type = excluded.connector_type,
       active = excluded.active,
       valid_from = excluded.valid_from,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 1000) AS n)
INSERT INTO pricing_components (
  id, pricing_plan_id, dimension, price, step_size, currency, sort_order, created_at, updated_at
)
SELECT
  ('af000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  'ENERGY',
  CASE
    WHEN n <= 800 THEN 0.320000 + ((n % 11)::numeric * 0.010000)
    ELSE 0.360000 + (((n - 800) % 9)::numeric * 0.012000)
  END,
  1,
  CASE WHEN n <= 800 THEN 'USD' ELSE 'EUR' END,
  1,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (id) DO UPDATE
   SET pricing_plan_id = excluded.pricing_plan_id,
       dimension = excluded.dimension,
       price = excluded.price,
       step_size = excluded.step_size,
       currency = excluded.currency,
       sort_order = excluded.sort_order,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 1000) AS n)
INSERT INTO pricing_components (
  id, pricing_plan_id, dimension, price, step_size, currency, sort_order, created_at, updated_at
)
SELECT
  ('bf000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  'TIME',
  CASE
    WHEN n <= 800 THEN 0.030000 + ((n % 6)::numeric * 0.005000)
    ELSE 0.025000 + (((n - 800) % 5)::numeric * 0.006000)
  END,
  60,
  CASE WHEN n <= 800 THEN 'USD' ELSE 'EUR' END,
  2,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (id) DO UPDATE
   SET pricing_plan_id = excluded.pricing_plan_id,
       dimension = excluded.dimension,
       price = excluded.price,
       step_size = excluded.step_size,
       currency = excluded.currency,
       sort_order = excluded.sort_order,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 1000) AS n)
INSERT INTO pricing_components (
  id, pricing_plan_id, dimension, price, step_size, currency, sort_order, created_at, updated_at
)
SELECT
  ('cf000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  'PARKING',
  CASE WHEN n <= 800 THEN (n % 4)::numeric ELSE ((n - 800) % 3)::numeric END,
  900,
  CASE WHEN n <= 800 THEN 'USD' ELSE 'EUR' END,
  3,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (id) DO UPDATE
   SET pricing_plan_id = excluded.pricing_plan_id,
       dimension = excluded.dimension,
       price = excluded.price,
       step_size = excluded.step_size,
       currency = excluded.currency,
       sort_order = excluded.sort_order,
       updated_at = current_timestamp;

WITH numbered AS (SELECT n FROM generate_series(1, 1000) AS n)
INSERT INTO pricing_components (
  id, pricing_plan_id, dimension, price, step_size, currency, sort_order, created_at, updated_at
)
SELECT
  ('df000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  'FLAT',
  CASE WHEN n <= 800 THEN (n % 2)::numeric ELSE ((n - 800) % 2)::numeric END,
  1,
  CASE WHEN n <= 800 THEN 'USD' ELSE 'EUR' END,
  4,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (id) DO UPDATE
   SET pricing_plan_id = excluded.pricing_plan_id,
       dimension = excluded.dimension,
       price = excluded.price,
       step_size = excluded.step_size,
       currency = excluded.currency,
       sort_order = excluded.sort_order,
       updated_at = current_timestamp;

COMMIT;

\echo 'Loading ElectraHub demo billing tariffs into billing_db'
\connect billing_db
BEGIN;

WITH numbered AS (SELECT n FROM generate_series(1, 1000) AS n)
INSERT INTO tariffs (
  id, name, description, currency, energy_price, time_price, parking_price,
  flat_fee, min_price, max_price, valid_from, valid_to, location_id,
  active, created_at, updated_at
)
SELECT
  ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  CASE
    WHEN n <= 800 THEN 'ElectraHub USA Demo Tariff ' || lpad(n::text, 3, '0')
    ELSE 'ElectraHub Europe Demo Tariff ' || lpad((n - 800)::text, 3, '0')
  END,
  CASE
    WHEN n <= 800 THEN 'USA demo tariff linked from seeded connector CON-US-' || lpad(n::text, 4, '0')
    ELSE 'Europe demo tariff linked from seeded connector CON-EU-' || lpad((n - 800)::text, 4, '0')
  END,
  CASE WHEN n <= 800 THEN 'USD' ELSE 'EUR' END,
  CASE
    WHEN n <= 800 THEN 0.3200 + ((n % 11)::numeric * 0.0100)
    ELSE 0.3600 + (((n - 800) % 9)::numeric * 0.0120)
  END,
  CASE
    WHEN n <= 800 THEN 0.0300 + ((n % 6)::numeric * 0.0050)
    ELSE 0.0250 + (((n - 800) % 5)::numeric * 0.0060)
  END,
  CASE WHEN n <= 800 THEN (n % 4)::numeric ELSE ((n - 800) % 3)::numeric END,
  CASE WHEN n <= 800 THEN (n % 2)::numeric ELSE ((n - 800) % 2)::numeric END,
  0.00,
  null,
  timestamp with time zone '2026-01-01 00:00:00+00',
  null,
  null,
  true,
  current_timestamp,
  current_timestamp
FROM numbered
ON CONFLICT (id) DO UPDATE
   SET name = excluded.name,
       description = excluded.description,
       currency = excluded.currency,
       energy_price = excluded.energy_price,
       time_price = excluded.time_price,
       parking_price = excluded.parking_price,
       flat_fee = excluded.flat_fee,
       min_price = excluded.min_price,
       max_price = excluded.max_price,
       valid_from = excluded.valid_from,
       valid_to = excluded.valid_to,
       location_id = excluded.location_id,
       active = excluded.active,
       updated_at = current_timestamp;

COMMIT;

\echo 'Loading ElectraHub demo subscriptions into subscription_db'
\connect subscription_db
BEGIN;

INSERT INTO subscription_mgmt.subscription_plans (
  id, code, name, description, currency_code,
  total_fee_discount_type, total_fee_discount_value,
  session_fee_discount_type, session_fee_discount_value,
  default_quota_limit, active, created_at, updated_at,
  visibility, plan_category, pricing_model, benefit_display_mode, quota_unit,
  default_quota_value, subscription_price_amount, validity_days,
  enterprise_id, country_code, public_sort_order, allow_stacking, created_by
) VALUES
  (
    '77000000-0000-0000-0000-000000000001'::uuid,
    'PUBLIC-US-POWER-10',
    'ElectraHub US Power 10',
    'Public US driver subscription with 10 percent charging discount and 500 kWh monthly quota.',
    'USD', 'PERCENTAGE', 10.0000, 'NONE', 0.0000,
    500, true, current_timestamp, current_timestamp,
    'PUBLIC', 'DRIVER_PUBLIC', 'PAID_RECURRING', 'DISCOUNT', 'KWH',
    500.0000, 9.9900, 30,
    null, 'US', 10, false, 'demo-data-loader'
  ),
  (
    '77000000-0000-0000-0000-000000000002'::uuid,
    'PUBLIC-US-FIXED-100',
    'ElectraHub US 100 kWh Pass',
    'Fixed-price prepaid public US pass that includes 100 kWh.',
    'USD', 'NONE', 0.0000, 'NONE', 0.0000,
    100, true, current_timestamp, current_timestamp,
    'PUBLIC', 'DRIVER_PUBLIC', 'FIXED_PRICE_PREPAID', 'INCLUDED_QUOTA', 'KWH',
    100.0000, 39.9900, 60,
    null, 'US', 20, false, 'demo-data-loader'
  ),
  (
    '77000000-0000-0000-0000-000000000003'::uuid,
    'PUBLIC-EU-POWER-12',
    'ElectraHub Europe Power 12',
    'Public EU driver subscription with 12 percent charging discount and 500 kWh monthly quota.',
    'EUR', 'PERCENTAGE', 12.0000, 'NONE', 0.0000,
    500, true, current_timestamp, current_timestamp,
    'PUBLIC', 'DRIVER_PUBLIC', 'PAID_RECURRING', 'DISCOUNT', 'KWH',
    500.0000, 8.9900, 30,
    null, 'NL', 30, false, 'demo-data-loader'
  ),
  (
    '77000000-0000-0000-0000-000000000004'::uuid,
    'BMW-NEW-CAR-1000KWH',
    'BMW New Vehicle 1000 kWh Grant',
    'Admin/OEM grant for BMW customers receiving 1000 included kWh on new vehicle purchase.',
    'USD', 'NONE', 0.0000, 'NONE', 0.0000,
    1000, true, current_timestamp, current_timestamp,
    'ADMIN_ONLY', 'OEM_PROMOTION', 'GRANT', 'INCLUDED_QUOTA', 'KWH',
    1000.0000, 0.0000, 365,
    null, 'US', null, false, 'demo-data-loader'
  ),
  (
    '77000000-0000-0000-0000-000000000005'::uuid,
    'EVGO-FLEET-250KWH',
    'EVgo Fleet 250 kWh Grant',
    'Private fleet grant sample with 250 included kWh.',
    'USD', 'NONE', 0.0000, 'NONE', 0.0000,
    250, true, current_timestamp, current_timestamp,
    'PRIVATE', 'FLEET', 'GRANT', 'INCLUDED_QUOTA', 'KWH',
    250.0000, 0.0000, 180,
    null, 'US', null, true, 'demo-data-loader'
  )
ON CONFLICT (code) DO UPDATE
   SET name = excluded.name,
       description = excluded.description,
       currency_code = excluded.currency_code,
       total_fee_discount_type = excluded.total_fee_discount_type,
       total_fee_discount_value = excluded.total_fee_discount_value,
       session_fee_discount_type = excluded.session_fee_discount_type,
       session_fee_discount_value = excluded.session_fee_discount_value,
       default_quota_limit = excluded.default_quota_limit,
       active = excluded.active,
       updated_at = current_timestamp,
       visibility = excluded.visibility,
       plan_category = excluded.plan_category,
       pricing_model = excluded.pricing_model,
       benefit_display_mode = excluded.benefit_display_mode,
       quota_unit = excluded.quota_unit,
       default_quota_value = excluded.default_quota_value,
       subscription_price_amount = excluded.subscription_price_amount,
       validity_days = excluded.validity_days,
       enterprise_id = excluded.enterprise_id,
       country_code = excluded.country_code,
       public_sort_order = excluded.public_sort_order,
       allow_stacking = excluded.allow_stacking,
       created_by = excluded.created_by;

INSERT INTO subscription_mgmt.subscription_allocations (
  id, plan_id, allocation_type, user_id, organization_id, group_id,
  quota_limit, consumed_units, starts_at, ends_at, status, created_by,
  created_at, updated_at, version, quota_limit_value, consumed_value,
  source, source_label, grant_reason, external_reference, vin,
  enterprise_id, last_used_at
) VALUES
  (
    '88000000-0000-0000-0000-000000000001'::uuid,
    '77000000-0000-0000-0000-000000000004'::uuid,
    'USER', :'demo_user_id'::uuid, null, null,
    1000, 0, current_timestamp - interval '1 day', current_timestamp + interval '365 days', 'ACTIVE', 'demo-data-loader',
    current_timestamp, current_timestamp, 0, 1000.0000, 0.0000,
    'OEM_GRANTED', 'BMW New Vehicle Grant', '1000 kWh included with new BMW purchase.', 'BMW-DEMO-ORDER-0001', 'WBADEMO0000000001',
    null, null
  ),
  (
    '88000000-0000-0000-0000-000000000002'::uuid,
    '77000000-0000-0000-0000-000000000001'::uuid,
    'USER', :'demo_user_id'::uuid, null, null,
    500, 0, current_timestamp - interval '1 day', current_timestamp + interval '30 days', 'ACTIVE', 'demo-data-loader',
    current_timestamp, current_timestamp, 0, 500.0000, 0.0000,
    'SELF_SUBSCRIBED', 'US Power 10 Demo Subscription', 'Public subscription seed allocation.', 'PUBLIC-US-POWER-10-DEMO', null,
    null, null
  )
ON CONFLICT (id) DO UPDATE
   SET plan_id = excluded.plan_id,
       allocation_type = excluded.allocation_type,
       user_id = excluded.user_id,
       organization_id = excluded.organization_id,
       group_id = excluded.group_id,
       quota_limit = excluded.quota_limit,
       starts_at = excluded.starts_at,
       ends_at = excluded.ends_at,
       status = excluded.status,
       created_by = excluded.created_by,
       updated_at = current_timestamp,
       quota_limit_value = excluded.quota_limit_value,
       source = excluded.source,
       source_label = excluded.source_label,
       grant_reason = excluded.grant_reason,
       external_reference = excluded.external_reference,
       vin = excluded.vin,
       enterprise_id = excluded.enterprise_id;

INSERT INTO subscription_mgmt.subscription_audit_logs (
  id, plan_id, allocation_id, user_id, organization_id, group_id, action, actor, detail, created_at
) VALUES
  (
    '99000000-0000-0000-0000-000000000001'::uuid,
    '77000000-0000-0000-0000-000000000004'::uuid,
    '88000000-0000-0000-0000-000000000001'::uuid,
    :'demo_user_id'::uuid,
    null,
    null,
    'ALLOCATION_GRANTED',
    'demo-data-loader',
    'Seeded BMW 1000 kWh demo grant allocation.',
    current_timestamp
  )
ON CONFLICT (id) DO UPDATE
   SET plan_id = excluded.plan_id,
       allocation_id = excluded.allocation_id,
       user_id = excluded.user_id,
       action = excluded.action,
       actor = excluded.actor,
       detail = excluded.detail,
       created_at = current_timestamp;

COMMIT;

\echo 'Validation summary'
\connect charger_management_db
SELECT 'charger_demo_fleet' AS metric,
       COUNT(*) AS chargers,
       COUNT(DISTINCT location_id) AS unique_charger_locations
FROM charger_inventory
WHERE charger_id LIKE 'EH-US-CHG-%' OR charger_id LIKE 'EH-EU-CHG-%';

SELECT 'location_demo_fleet' AS metric,
       COUNT(*) AS locations,
       COUNT(DISTINCT ocpi_location_id) AS unique_ocpi_locations
FROM locations
WHERE location_id LIKE 'LOC-USA-%' OR location_id LIKE 'LOC-EUR-%';

SELECT 'connector_demo_fleet' AS metric,
       COUNT(*) AS connectors,
       COUNT(DISTINCT ocpi_tariff_ids) AS unique_tariffs
FROM connector_inventory
WHERE connector_id LIKE 'CON-US-%' OR connector_id LIKE 'CON-EU-%';

\connect pricing_db
SELECT 'pricing_demo_fleet' AS metric,
       COUNT(*) AS pricing_plans,
       (SELECT COUNT(*) FROM pricing_components WHERE pricing_plan_id IN (
          SELECT ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid
          FROM generate_series(1, 1000) AS n
        )) AS pricing_components
FROM pricing_plans
WHERE id IN (
  SELECT ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid
  FROM generate_series(1, 1000) AS n
);

\connect billing_db
SELECT 'billing_tariff_demo_fleet' AS metric,
       COUNT(*) AS tariffs
FROM tariffs
WHERE id IN (
  SELECT ('9f000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid
  FROM generate_series(1, 1000) AS n
);

\connect subscription_db
SELECT 'subscription_seed' AS metric,
       COUNT(*) AS plans,
       (SELECT COUNT(*) FROM subscription_mgmt.subscription_allocations WHERE id::text LIKE '88000000-%') AS allocations
FROM subscription_mgmt.subscription_plans
WHERE code IN ('PUBLIC-US-POWER-10', 'PUBLIC-US-FIXED-100', 'PUBLIC-EU-POWER-12', 'BMW-NEW-CAR-1000KWH', 'EVGO-FLEET-250KWH');
