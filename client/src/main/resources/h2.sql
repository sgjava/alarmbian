-- H2 database

-- drop tables
DROP TABLE events IF EXISTS;

-- events
CREATE TABLE IF NOT EXISTS events
  (
    events_id identity not null primary key,
    event_time TIMESTAMP,
    device_name VARCHAR2( 50 ) NOT NULL,
    event_type VARCHAR2( 50 ) NOT NULL,
    event_data VARCHAR2( 200 ) NULL
  );
