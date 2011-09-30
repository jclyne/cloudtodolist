
CREATE TABLE IF NOT EXISTS entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT ,
  title VARCHAR(32),
  notes VARCHAR(1024),
  complete INTEGER DEFAULT 0,
  created DOUBLE,
  modified DOUBLE,
  deleted INTEGER DEFAULT 0
);

