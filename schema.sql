-- Jalankan sekali di MySQL Workbench / client MySQL
CREATE DATABASE IF NOT EXISTS db_puskesmas;
USE db_puskesmas;

CREATE TABLE IF NOT EXISTS poli (
    id    INT AUTO_INCREMENT PRIMARY KEY,
    kode  VARCHAR(3)   NOT NULL UNIQUE,
    nama  VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS pasien (
    no_rm      VARCHAR(20)  PRIMARY KEY,
    nama       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS antrian (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    poli_id       INT         NOT NULL,
    tanggal       DATE        NOT NULL,
    nomor         INT         NOT NULL,
    no_rm         VARCHAR(20) NOT NULL,
    status        ENUM('menunggu','dipanggil','selesai','batal') NOT NULL DEFAULT 'menunggu',
    waktu_daftar  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    waktu_panggil DATETIME    NULL,
    waktu_selesai DATETIME    NULL,
    CONSTRAINT uq_nomor UNIQUE (poli_id, tanggal, nomor),
    CONSTRAINT fk_poli  FOREIGN KEY (poli_id) REFERENCES poli(id),
    CONSTRAINT fk_pasien FOREIGN KEY (no_rm)  REFERENCES pasien(no_rm)
);

INSERT IGNORE INTO poli (kode, nama) VALUES
    ('A','Poli Umum'), ('B','Poli Gigi'), ('C','KIA / KB'),
    ('D','Poli Lansia'), ('E','Farmasi');

SELECT * FROM poli;
