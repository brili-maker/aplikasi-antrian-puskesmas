import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Layar ruang tunggu: menampilkan nomor yang sedang dipanggil di tiap poli, diperbarui tiap 3 detik. */
public class TvDisplay extends JFrame {
    private static final Color HIJAU = new Color(0x0F766E);
    private final JPanel grid = new JPanel();
    private final JLabel info = new JLabel(" ", SwingConstants.CENTER);
    private final Map<String, String> terakhir = new HashMap<>();
    private boolean pertama = true;
    private final Timer timer;

    public TvDisplay() {
        super("Layar Antrian Puskesmas");
        getContentPane().setBackground(HIJAU);
        setLayout(new BorderLayout());

        JLabel judul = new JLabel("ANTRIAN PUSKESMAS", SwingConstants.CENTER);
        judul.setFont(judul.getFont().deriveFont(Font.BOLD, 40f));
        judul.setForeground(Color.WHITE);
        judul.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        info.setForeground(Color.WHITE);
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(judul, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(info, BorderLayout.SOUTH);

        setSize(1000, 600);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        timer = new Timer(3000, e -> refresh());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
        });
        timer.start();
        refresh();
    }

    private void refresh() {
        Async.run(AntrianDatabase::statusPoli, this::tampilkan,
                ex -> info.setText("Gagal memuat data: " + ex.getMessage()));
    }

    private void tampilkan(List<String[]> rows) {
        info.setText(" ");
        grid.removeAll();
        grid.setLayout(new GridLayout(0, 3, 16, 16));
        for (String[] r : rows) {
            String kode = r[0], tiket = r[2];
            if (!pertama && tiket != null && !tiket.equals(terakhir.get(kode)))
                Toolkit.getDefaultToolkit().beep();   // bunyi saat nomor baru dipanggil
            terakhir.put(kode, tiket);

            JPanel kartu = new JPanel(new BorderLayout());
            kartu.setBackground(Color.WHITE);
            JLabel nama = new JLabel(r[1], SwingConstants.CENTER);
            nama.setFont(nama.getFont().deriveFont(Font.BOLD, 24f));
            JLabel nomor = new JLabel(tiket == null ? "-" : tiket, SwingConstants.CENTER);
            nomor.setFont(nomor.getFont().deriveFont(Font.BOLD, 72f));
            nomor.setForeground(HIJAU);
            JLabel tunggu = new JLabel(r[3] + " menunggu", SwingConstants.CENTER);
            tunggu.setFont(tunggu.getFont().deriveFont(18f));
            kartu.add(nama, BorderLayout.NORTH);
            kartu.add(nomor, BorderLayout.CENTER);
            kartu.add(tunggu, BorderLayout.SOUTH);
            grid.add(kartu);
        }
        pertama = false;
        grid.revalidate();
        grid.repaint();
    }
}
