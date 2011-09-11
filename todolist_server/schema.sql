DROP DATABASE IF EXISTS todolist;
CREATE DATABASE todolist;
USE todolist;

CREATE TABLE entries (
    id INTEGER NOT NULL AUTO_INCREMENT,
    title CHAR(32),
    notes VARCHAR(256),
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    primary key (id)
);