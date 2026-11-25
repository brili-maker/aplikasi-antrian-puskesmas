import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;



public class Main {
    private final JFrame frame = new JFrame("Aplikasi Antrian Puskesmas");
    private final JTextField txtNoRm = new JTextField(12), txtNama = new JTextField(18), txtCari = new JTextField(12);
    private final JComboBox<AntrianDatabase.Poli> cbPoli = new JComboBox<>();
    private final JComboBox<Object> cbFilter = new JComboBox<>();
    private final JLabel lblPanggil = new JLabel("Sedang dipanggil: -", SwingConstants.CENTER);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "No. Antrian", "No. RM", "Nama", "Poli", "Status", "Jam"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private boolean memuatPoli = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().start());
    }

    private void start() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setBorder(BorderFactory.createTitledBorder("Pendaftaran Pasien"));
        JButton btnAmbil = new JButton("Ambil Nomor"), btnReset = new JButton("Reset");
        form.add(new JLabel("No. RM")); form.add(txtNoRm);
        form.add(new JLabel("Nama"));   form.add(txtNama);
        form.add(new JLabel("Poli"));   form.add(cbPoli);
        form.add(btnAmbil); form.add(btnReset);

        JButton btnPanggil = new JButton("Panggil Berikutnya"), btnSelesai = new JButton("Selesai"),
                btnBatal = new JButton("Tidak Hadir"), btnHapus = new JButton("Hapus"),
                btnEdit = new JButton("Edit Pasien"), btnCari = new JButton("Cari"),
                btnRefresh = new JButton("Refresh"), btnLaporan = new JButton("Laporan Harian"),
                btnTv = new JButton("Layar TV");
        JPanel baris1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        baris1.add(new JLabel("Poli")); baris1.add(cbFilter);
        baris1.add(btnPanggil); baris1.add(btnSelesai); baris1.add(btnBatal); baris1.add(btnHapus); baris1.add(btnEdit);
        JPanel baris2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        baris2.add(new JLabel("Cari")); baris2.add(txtCari); baris2.add(btnCari); baris2.add(btnRefresh);
        baris2.add(btnLaporan); baris2.add(btnTv);
        JPanel loket = new JPanel(new GridLayout(2, 1));
        loket.setBorder(BorderFactory.createTitledBorder("Loket Petugas"));
        loket.add(baris1); loket.add(baris2);

        lblPanggil.setFont(lblPanggil.getFont().deriveFont(Font.BOLD, 22f));
        JPanel atas = new JPanel(new BorderLayout());
        atas.add(form, BorderLayout.NORTH);
        atas.add(loket, BorderLayout.CENTER);
        atas.add(lblPanggil, BorderLayout.SOUTH);
        frame.add(atas, BorderLayout.NORTH);
        frame.add(new JScrollPane(table), BorderLayout.CENTER);

        btnAmbil.addActionListener(e -> ambilNomor());
        btnReset.addActionListener(e -> { txtNoRm.setText(""); txtNama.setText(""); txtNoRm.requestFocus(); });
        btnPanggil.addActionListener(e -> panggil());
        btnSelesai.addActionListener(e -> ubahStatus("selesai"));
        btnBatal.addActionListener(e -> ubahStatus("batal"));
        btnHapus.addActionListener(e -> hapus());
        btnEdit.addActionListener(e -> editPasien());
        btnCari.addActionListener(e -> muatTabel());
        btnRefresh.addActionListener(e -> { txtCari.setText(""); muatTabel(); });
        btnLaporan.addActionListener(e -> bukaLaporan());
        btnTv.addActionListener(e -> new TvDisplay().setVisible(true));
        cbFilter.addActionListener(e -> { if (!memuatPoli) muatTabel(); });

        frame.setSize(1050, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        muatPoli();
    }


    private void muatPoli() {
        Async.run(AntrianDatabase::findPoli, list -> {
            memuatPoli = true;
            cbPoli.removeAllItems(); cbFilter.removeAllItems();
            cbFilter.addItem("Semua Poli");
            for (AntrianDatabase.Poli p : list) { cbPoli.addItem(p); cbFilter.addItem(p); }
            memuatPoli = false;
            muatTabel();
        }, this::gagal);
    }

    private void muatTabel() {
        Integer poliId = poliTerpilih();
        String kata = txtCari.getText();
        Async.run(() -> AntrianDatabase.findAll(poliId, kata), rows -> {
            model.setRowCount(0);
            for (String[] r : rows) model.addRow(r);
        }, this::gagal);
    }

    private void ambilNomor() {
        String noRm = txtNoRm.getText().trim(), nama = txtNama.getText().trim();
        if (noRm.isEmpty() || nama.isEmpty()) { warn("No. RM dan nama wajib diisi."); return; }
        if (!noRm.matches("[A-Za-z0-9\\-]{1,20}")) { warn("No. RM hanya huruf/angka/tanda minus (maks. 20)."); return; }
        AntrianDatabase.Poli poli = (AntrianDatabase.Poli) cbPoli.getSelectedItem();
        if (poli == null) { warn("Pilih poli."); return; }
        Async.run(() -> AntrianDatabase.ambilNomor(noRm, nama, poli.id), tiket -> {
            JOptionPane.showMessageDialog(frame, "Nomor antrian Anda: " + tiket + "\n" + poli.nama);
            txtNoRm.setText(""); txtNama.setText("");
            muatTabel();
        }, this::gagal);
    }

    private void panggil() {
        Integer poliId = poliTerpilih();
        if (poliId == null) { warn("Pilih satu poli pada filter untuk memanggil antrian."); return; }
        Async.run(() -> AntrianDatabase.panggilBerikutnya(poliId), hasil -> {
            lblPanggil.setText(hasil == null ? "Tidak ada antrian yang menunggu" : "Sedang dipanggil: " + hasil);
            muatTabel();
        }, this::gagal);
    }

    private void ubahStatus(String status) {
        int id = idTerpilih();
        if (id < 0) return;
        Async.run(() -> { AntrianDatabase.ubahStatus(id, status); return null; }, x -> muatTabel(), this::gagal);
    }

    private void hapus() {
        int id = idTerpilih();
        if (id < 0) return;
        if (JOptionPane.showConfirmDialog(frame, "Hapus antrian ini?", "Konfirmasi",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Async.run(() -> { AntrianDatabase.hapus(id); return null; }, x -> muatTabel(), this::gagal);
    }

 
    private void editPasien() {
        int row = table.getSelectedRow();
        if (row < 0) { warn("Pilih satu baris pada tabel."); return; }
        String noRm = (String) model.getValueAt(row, 2), lama = (String) model.getValueAt(row, 3);
        Object input = JOptionPane.showInputDialog(frame, "Nama baru untuk No. RM " + noRm + ":",
                "Edit Pasien", JOptionPane.PLAIN_MESSAGE, null, null, lama);
        if (input == null) return;
        String nama = input.toString().trim();
        if (nama.isEmpty()) { warn("Nama tidak boleh kosong."); return; }
        Async.run(() -> { AntrianDatabase.updatePasien(noRm, nama); return null; }, x -> muatTabel(), this::gagal);
    }

    private void bukaLaporan() {
        JDialog d = new JDialog(frame, "Laporan Harian", true);
        JTextField tgl = new JTextField(LocalDate.now().toString(), 10);
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Poli", "Total", "Selesai", "Batal", "Masih Antri", "Rata-rata Tunggu (menit)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        Runnable muat = () -> {
            String t = tgl.getText().trim();
            try { LocalDate.parse(t); } catch (DateTimeParseException ex) { warn("Format tanggal: yyyy-MM-dd"); return; }
            Async.run(() -> AntrianDatabase.laporan(t), rows -> {
                m.setRowCount(0);
                int[] total = new int[4];
                for (String[] r : rows) {
                    m.addRow(r);
                    for (int i = 0; i < 4; i++) total[i] += (int) Double.parseDouble(r[i + 1]);
                }
                m.addRow(new Object[]{"TOTAL", total[0], total[1], total[2], total[3], ""});
            }, this::gagal);
        };
        JButton btn = new JButton("Tampilkan");
        btn.addActionListener(e -> muat.run());
        JPanel atas = new JPanel(new FlowLayout(FlowLayout.LEFT));
        atas.add(new JLabel("Tanggal (yyyy-MM-dd)")); atas.add(tgl); atas.add(btn);
        d.add(atas, BorderLayout.NORTH);
        d.add(new JScrollPane(new JTable(m)), BorderLayout.CENTER);
        d.setSize(720, 330);
        d.setLocationRelativeTo(frame);
        muat.run();
        d.setVisible(true);
    }

  
    private Integer poliTerpilih() {
        Object o = cbFilter.getSelectedItem();
        return o instanceof AntrianDatabase.Poli ? ((AntrianDatabase.Poli) o).id : null;
    }

    private int idTerpilih() {
        int row = table.getSelectedRow();
        if (row < 0) { warn("Pilih satu baris pada tabel."); return -1; }
        return Integer.parseInt((String) model.getValueAt(row, 0));
    }

    private void warn(String msg) { JOptionPane.showMessageDialog(frame, msg, "Perhatian", JOptionPane.WARNING_MESSAGE); }

    private void gagal(Exception ex) {
        if (ex instanceof IllegalStateException) { warn(ex.getMessage()); return; }
        JOptionPane.showMessageDialog(frame, "Gagal mengakses database:\n" + ex.getMessage()
                + "\n\nPeriksa MySQL aktif dan db.properties.", "Error", JOptionPane.ERROR_MESSAGE);
    }
}
