DROP DATABASE IF EXISTS todolist;
CREATE DATABASE todolist;
USE todolist;

CREATE TABLE users
(
  id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL,
  password VARCHAR(128) NOT NULL,
  PRIMARY KEY(id),
  UNIQUE KEY(username),
  KEY(password)
) TYPE=innodb;

CREATE TABLE entries (
  id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,
  title CHAR(32),
  notes VARCHAR(256),
  complete BOOLEAN NOT NULL DEFAULT FALSE,
  user_id INTEGER UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) TYPE=innodb;




