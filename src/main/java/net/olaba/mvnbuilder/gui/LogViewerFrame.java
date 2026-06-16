package net.olaba.mvnbuilder.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * A graphical window using Swing to view the application logs in real-time.
 * Features a dark terminal theme and auto-scrolls to the newest logs.
 */
public class LogViewerFrame extends JFrame {

    private final JTextArea textArea;
    private final File logFile;
    private long lastReadOffset = 0;
    private Timer timer;

    public LogViewerFrame() {
        setTitle("MvnBuilder - Log Viewer");
        setSize(850, 550);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Dark terminal theme colors
        final Color backgroundColor = new Color(30, 30, 30);
        final Color textColor = new Color(220, 220, 220);
        final Color accentColor = new Color(74, 144, 226); // Nice indigo/blue accent

        // Panel layout
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(backgroundColor);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header Label
        final JLabel headerLabel = new JLabel("MvnBuilder Application Logs");
        headerLabel.setForeground(accentColor);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(headerLabel, BorderLayout.NORTH);

        // Text Area for logs
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setBackground(new Color(20, 20, 20));
        textArea.setForeground(textColor);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setCaretColor(textColor);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        // Scroll Pane
        final JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));
        scrollPane.getVerticalScrollBar().setBackground(backgroundColor);
        scrollPane.getHorizontalScrollBar().setBackground(backgroundColor);
        panel.add(scrollPane, BorderLayout.CENTER);

        add(panel);

        // Load log file from user home (~/.mvnbuilder/mvnbuilder.log)
        final String homeDir = System.getProperty("user.home");
        logFile = Paths.get(homeDir, ".mvnbuilder", "mvnbuilder.log").toFile();

        if (!logFile.exists()) {
            textArea.append("El archivo de logs aún no se ha creado en la ruta:\n" + logFile.getAbsolutePath() + "\n\nEsperando a que la aplicación comience a escribir registros...\n");
            // Set offset to 0 so we can read it when created
            lastReadOffset = 0;
        } else {
            readExistingLogs();
        }

        startTailTimer();
    }

    /**
     * Reads all existing log entries from the file on initialization.
     */
    private void readExistingLogs() {
        if (!logFile.exists()) {
            return;
        }

        try (final RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            final long len = logFile.length();
            // Read at most last 100KB of logs initially to prevent overload
            final long startPosition = Math.max(0, len - 100 * 1024);
            raf.seek(startPosition);

            final long byteCount = len - startPosition;
            final byte[] bytes = new byte[(int) byteCount];
            raf.readFully(bytes);

            textArea.setText(new String(bytes, StandardCharsets.UTF_8));
            textArea.setCaretPosition(textArea.getDocument().getLength());
            lastReadOffset = len;
        } catch (final Exception e) {
            textArea.append("Error al leer logs iniciales: " + e.getMessage() + "\n");
        }
    }

    /**
     * Starts a timer that polls the log file every second for new appends (like tail -f).
     */
    private void startTailTimer() {
        timer = new Timer(1000, e -> {
            if (logFile.exists() && logFile.length() > lastReadOffset) {
                try (final RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                    raf.seek(lastReadOffset);
                    final long len = logFile.length() - lastReadOffset;
                    final byte[] bytes = new byte[(int) len];
                    raf.readFully(bytes);

                    textArea.append(new String(bytes, StandardCharsets.UTF_8));
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                    lastReadOffset = logFile.length();
                } catch (final Exception ex) {
                    // Fail silently
                }
            }
        });
        timer.start();
    }

    @Override
    public void dispose() {
        if (timer != null) {
            timer.stop();
        }
        super.dispose();
    }
}
