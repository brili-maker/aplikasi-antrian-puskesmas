import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Membaca konfigurasi koneksi dari db.properties (tidak di-commit). */
final class DbConfig {
    static final String URL, USER, PASSWORD;

    static {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream("db.properties")) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("db.properties tidak ditemukan, memakai nilai bawaan.");
        }
        URL = p.getProperty("db.url", "jdbc:mysql://localhost:3306/db_puskesmas");
        USER = p.getProperty("db.user", "root");
        PASSWORD = p.getProperty("db.password", "");
    }

    private DbConfig() {}
}
