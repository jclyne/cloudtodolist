DROP DATABASE IF EXISTS todolist_example;
CREATE DATABASE todolist_example;
USE todolist_example;

CREATE TABLE entries (
    id INTEGER NOT NULL AUTO_INCREMENT,
    title CHAR(32),
    notes VARCHAR(256),
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    primary key (id)
);