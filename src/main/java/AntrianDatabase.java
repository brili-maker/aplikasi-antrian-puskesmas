import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Lapisan akses data: semua SQL ada di sini, GUI hanya memanggil method-nya. */
public class AntrianDatabase {

    public static class Poli {
        public final int id;
        public final String kode, nama;
        Poli(int id, String kode, String nama) { this.id = id; this.kode = kode; this.nama = nama; }
        @Override public String toString() { return kode + " - " + nama; }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(DbConfig.URL, DbConfig.USER, DbConfig.PASSWORD);
    }

    public static List<Poli> findPoli() throws SQLException {
        List<Poli> list = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT id, kode, nama FROM poli ORDER BY kode");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Poli(rs.getInt(1), rs.getString(2), rs.getString(3)));
        }
        return list;
    }

    /** Daftarkan pasien (jika baru) dan ambil nomor antrian berikutnya. Mengembalikan mis. "A-003". */
    public static String ambilNomor(String noRm, String nama, int poliId) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                String kode;
                // kunci baris poli agar dua loket tidak mendapat nomor yang sama
                try (PreparedStatement ps = c.prepareStatement("SELECT kode FROM poli WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, poliId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Poli tidak ditemukan.");
                        kode = rs.getString(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pasien (no_rm, nama) VALUES (?, ?) "
                      + "ON DUPLICATE KEY UPDATE nama = VALUES(nama)")) {
                    ps.setString(1, noRm);
                    ps.setString(2, nama);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM antrian WHERE no_rm=? AND poli_id=? AND tanggal=CURDATE() "
                      + "AND status IN ('menunggu','dipanggil')")) {
                    ps.setString(1, noRm);
                    ps.setInt(2, poliId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0)
                            throw new IllegalStateException("Pasien masih punya antrian aktif di poli ini.");
                    }
                }
                int nomor;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(MAX(nomor),0)+1 FROM antrian WHERE poli_id=? AND tanggal=CURDATE()")) {
                    ps.setInt(1, poliId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); nomor = rs.getInt(1); }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO antrian (poli_id, tanggal, nomor, no_rm) VALUES (?, CURDATE(), ?, ?)")) {
                    ps.setInt(1, poliId);
                    ps.setInt(2, nomor);
                    ps.setString(3, noRm);
                    ps.executeUpdate();
                }
                c.commit();
                return String.format("%s-%03d", kode, nomor);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Antrian hari ini. poliId null = semua poli; keyword boleh kosong (cari nama / No. RM). */
    public static List<String[]> findAll(Integer poliId, String keyword) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, CONCAT(p.kode,'-',LPAD(a.nomor,3,'0')), a.no_rm, s.nama, p.nama, a.status, "
          + "DATE_FORMAT(a.waktu_daftar,'%H:%i') FROM antrian a "
          + "JOIN poli p ON p.id=a.poli_id JOIN pasien s ON s.no_rm=a.no_rm WHERE a.tanggal=CURDATE()");
        if (poliId != null) sql.append(" AND a.poli_id=?");
        boolean cari = keyword != null && !keyword.trim().isEmpty();
        if (cari) sql.append(" AND (s.nama LIKE ? OR s.no_rm LIKE ?)");
        sql.append(" ORDER BY p.kode, a.nomor");

        List<String[]> rows = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if (poliId != null) ps.setInt(i++, poliId);
            if (cari) { String k = "%" + keyword.trim() + "%"; ps.setString(i++, k); ps.setString(i, k); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] r = new String[7];
                    for (int j = 0; j < 7; j++) r[j] = rs.getString(j + 1);
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    /** Selesaikan antrian yang sedang dipanggil lalu panggil berikutnya. null jika tidak ada yang menunggu. */
    public static String panggilBerikutnya(int poliId) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM poli WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, poliId);
                    ps.executeQuery().close();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE antrian SET status='selesai', waktu_selesai=NOW() "
                      + "WHERE poli_id=? AND tanggal=CURDATE() AND status='dipanggil'")) {
                    ps.setInt(1, poliId);
                    ps.executeUpdate();
                }
                int id = -1; String hasil = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT a.id, CONCAT(p.kode,'-',LPAD(a.nomor,3,'0'),' - ',s.nama) FROM antrian a "
                      + "JOIN poli p ON p.id=a.poli_id JOIN pasien s ON s.no_rm=a.no_rm "
                      + "WHERE a.poli_id=? AND a.tanggal=CURDATE() AND a.status='menunggu' "
                      + "ORDER BY a.nomor LIMIT 1")) {
                    ps.setInt(1, poliId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) { id = rs.getInt(1); hasil = rs.getString(2); }
                    }
                }
                if (id > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE antrian SET status='dipanggil', waktu_panggil=NOW() WHERE id=?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return hasil;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** status: selesai / batal */
    public static void ubahStatus(int id, String status) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("UPDATE antrian SET status=?, waktu_selesai=NOW() WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void hapus(int id) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("DELETE FROM antrian WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Tantangan A (UPDATE): ubah nama pasien berdasarkan No. RM. */
    public static void updatePasien(String noRm, String nama) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("UPDATE pasien SET nama=? WHERE no_rm=?")) {
            ps.setString(1, nama);
            ps.setString(2, noRm);
            ps.executeUpdate();
        }
    }

    /** Laporan per poli pada tanggal tertentu (yyyy-MM-dd): total, selesai, batal, masih antri, rata-rata tunggu (menit). */
    public static List<String[]> laporan(String tanggal) throws SQLException {
        String sql = "SELECT p.nama, COUNT(a.id), "
                + "COALESCE(SUM(a.status='selesai'),0), COALESCE(SUM(a.status='batal'),0), "
                + "COALESCE(SUM(a.status IN ('menunggu','dipanggil')),0), "
                + "ROUND(AVG(TIMESTAMPDIFF(MINUTE, a.waktu_daftar, a.waktu_panggil)),1) "
                + "FROM poli p LEFT JOIN antrian a ON a.poli_id=p.id AND a.tanggal=? "
                + "GROUP BY p.id, p.kode, p.nama ORDER BY p.kode";
        List<String[]> rows = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tanggal);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rata = rs.getString(6);
                    rows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rata == null ? "-" : rata});
                }
            }
        }
        return rows;
    }

    /** Untuk layar TV: {kode, nama poli, nomor yang dipanggil (atau null), jumlah menunggu}. */
    public static List<String[]> statusPoli() throws SQLException {
        String sql = "SELECT p.kode, p.nama, "
                + "(SELECT CONCAT(p.kode,'-',LPAD(a.nomor,3,'0')) FROM antrian a WHERE a.poli_id=p.id "
                + " AND a.tanggal=CURDATE() AND a.status='dipanggil' LIMIT 1), "
                + "(SELECT COUNT(*) FROM antrian a WHERE a.poli_id=p.id AND a.tanggal=CURDATE() "
                + " AND a.status='menunggu') FROM poli p ORDER BY p.kode";
        List<String[]> rows = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                rows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
        }
        return rows;
    }
}
