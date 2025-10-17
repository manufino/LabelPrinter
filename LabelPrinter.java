import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LabelPrinter extends JFrame {
    private static final double A4_WIDTH_MM = 210.0;
    private static final double A4_HEIGHT_MM = 297.0;
    private static final double MM_TO_POINTS = 2.83465;
    
    private JTable labelTable;
    private DefaultTableModel tableModel;
    private PreviewPanel previewPanel;
    private JSpinner fontSizeSpinner;
    private JComboBox<String> fontFamilyCombo;
    private JCheckBox boldCheckBox;
    private JSpinner widthSpinner;
    private JSpinner heightSpinner;
    private int currentPage = 0;
    private JLabel pageLabel;
    
    public LabelPrinter() {
        setTitle("LabelPrinter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Panel sinistro con controlli e tabella
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Controlli dimensioni etichette
        JPanel dimensionsPanel = new JPanel();
        dimensionsPanel.setLayout(new BoxLayout(dimensionsPanel, BoxLayout.Y_AXIS));
        dimensionsPanel.setBorder(BorderFactory.createTitledBorder("Dimensioni Etichette (mm)"));
        
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sizePanel.add(new JLabel("Larghezza:"));
        widthSpinner = new JSpinner(new SpinnerNumberModel(28.0, 10.0, 100.0, 0.5));
        widthSpinner.addChangeListener(e -> {
            updateTitle();
            previewPanel.repaint();
        });
        sizePanel.add(widthSpinner);
        sizePanel.add(new JLabel("  Altezza:"));
        heightSpinner = new JSpinner(new SpinnerNumberModel(19.0, 10.0, 100.0, 0.5));
        heightSpinner.addChangeListener(e -> {
            updateTitle();
            previewPanel.repaint();
        });
        sizePanel.add(heightSpinner);
        dimensionsPanel.add(sizePanel);
        
        // Controlli font
        JPanel fontPanel = new JPanel();
        fontPanel.setLayout(new BoxLayout(fontPanel, BoxLayout.Y_AXIS));
        fontPanel.setBorder(BorderFactory.createTitledBorder("Impostazioni Font"));
        
        JPanel fontFamilyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fontFamilyPanel.add(new JLabel("Famiglia:"));
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontFamilyCombo = new JComboBox<>(fonts);
        fontFamilyCombo.setSelectedItem("Arial");
        fontFamilyCombo.addActionListener(e -> previewPanel.repaint());
        fontFamilyPanel.add(fontFamilyCombo);
        fontPanel.add(fontFamilyPanel);
        
        JPanel fontOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fontOptionsPanel.add(new JLabel("Dimensione:"));
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(8, 4, 20, 1));
        fontSizeSpinner.addChangeListener(e -> previewPanel.repaint());
        fontOptionsPanel.add(fontSizeSpinner);
        boldCheckBox = new JCheckBox("Grassetto");
        boldCheckBox.addActionListener(e -> previewPanel.repaint());
        fontOptionsPanel.add(boldCheckBox);
        fontPanel.add(fontOptionsPanel);
        
        // Controlli file e stampa
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createTitledBorder("Azioni"));
        
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadBtn = new JButton("Carica da File");
        loadBtn.addActionListener(e -> loadFromFile());
        filePanel.add(loadBtn);
        JButton saveBtn = new JButton("Esporta su File");
        saveBtn.addActionListener(e -> saveToFile());
        filePanel.add(saveBtn);
        controlsPanel.add(filePanel);
        
        JPanel printPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton printBtn = new JButton("Stampa Tutto");
        printBtn.addActionListener(e -> printLabels(false));
        printPanel.add(printBtn);
        JButton printCurrentBtn = new JButton("Stampa Corrente");
        printCurrentBtn.addActionListener(e -> printLabels(true));
        printPanel.add(printCurrentBtn);
        controlsPanel.add(printPanel);
        
        // Navigazione pagine
        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton prevBtn = new JButton("◄ Pag. Prec.");
        prevBtn.addActionListener(e -> {
            if (currentPage > 0) {
                currentPage--;
                updatePageLabel();
                previewPanel.repaint();
            }
        });
        pagePanel.add(prevBtn);
        
        pageLabel = new JLabel("Pagina 1");
        pagePanel.add(pageLabel);
        
        JButton nextBtn = new JButton("Pag. Succ. ►");
        nextBtn.addActionListener(e -> {
            if (currentPage < getTotalPages() - 1) {
                currentPage++;
                updatePageLabel();
                previewPanel.repaint();
            }
        });
        pagePanel.add(nextBtn);
        controlsPanel.add(pagePanel);
        
        // Assemblaggio pannello sinistro superiore
        JPanel topLeftPanel = new JPanel();
        topLeftPanel.setLayout(new BoxLayout(topLeftPanel, BoxLayout.Y_AXIS));
        topLeftPanel.add(dimensionsPanel);
        topLeftPanel.add(fontPanel);
        topLeftPanel.add(controlsPanel);
        
        leftPanel.add(topLeftPanel, BorderLayout.NORTH);
        
        // Tabella per inserimento etichette
        String[] columnNames = {"Riga 1", "Riga 2"};
        tableModel = new DefaultTableModel(columnNames, 20) {
            @Override
            public Class<?> getColumnClass(int column) {
                return String.class;
            }
        };
        
        labelTable = new JTable(tableModel);
        labelTable.getModel().addTableModelListener(e -> {
            updatePageLabel();
            updateTitle();
            previewPanel.repaint();
        });
        
        JScrollPane tableScroll = new JScrollPane(labelTable);
        
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Lista Etichette"));
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addRowBtn = new JButton("Aggiungi Riga");
        addRowBtn.addActionListener(e -> tableModel.addRow(new Object[]{"", ""}));
        buttonPanel.add(addRowBtn);
        
        JButton removeRowBtn = new JButton("Rimuovi Riga");
        removeRowBtn.addActionListener(e -> {
            int selectedRow = labelTable.getSelectedRow();
            if (selectedRow != -1) {
                tableModel.removeRow(selectedRow);
            }
        });
        buttonPanel.add(removeRowBtn);
        
        JButton clearBtn = new JButton("Cancella Tutto");
        clearBtn.addActionListener(e -> {
            tableModel.setRowCount(0);
            tableModel.setRowCount(20);
            currentPage = 0;
            updatePageLabel();
            updateTitle();
            previewPanel.repaint();
        });
        buttonPanel.add(clearBtn);
        
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);
        leftPanel.add(tablePanel, BorderLayout.CENTER);
        
        // Panel anteprima
        previewPanel = new PreviewPanel();
        JScrollPane previewScroll = new JScrollPane(previewPanel);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Anteprima Foglio A4"));
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);
        
        // Split pane resizable
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, previewScroll);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.0);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Calcola dimensioni finestra ottimali
        double mmToPixel = 96.0 / 25.4;
        int previewWidth = (int) (A4_WIDTH_MM * mmToPixel) + 40; // +40 per margini e scrollbar
        int leftPanelWidth = 350;
        int totalWidth = leftPanelWidth + previewWidth + 20; // +20 per divider e bordi
        
        // Ottieni dimensioni dello schermo
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = (int) (screenSize.width * 0.95); // Usa max 95% della larghezza schermo
        int maxHeight = (int) (screenSize.height * 0.9); // Usa max 90% dell'altezza schermo
        
        // Imposta dimensioni finestra (limitate dalle dimensioni dello schermo)
        int windowWidth = Math.min(totalWidth, maxWidth);
        int windowHeight = Math.min(900, maxHeight);
        
        setSize(windowWidth, windowHeight);
        setLocationRelativeTo(null);
        updateTitle();
        updatePageLabel();
    }
    
    private void updateTitle() {
        List<Label> labels = getLabels();
        if (labels.isEmpty()) {
            setTitle("LabelPrinter");
        } else {
            setTitle("LabelPrinter (" + labels.size() + " etichette)");
        }
    }
    
    private double getLabelWidth() {
        return (Double) widthSpinner.getValue();
    }
    
    private double getLabelHeight() {
        return (Double) heightSpinner.getValue();
    }
    
    private int getLabelsPerPage() {
        int cols = (int) (A4_WIDTH_MM / getLabelWidth());
        int rows = (int) (A4_HEIGHT_MM / getLabelHeight());
        return cols * rows;
    }
    
    private int getTotalPages() {
        List<Label> labels = getLabels();
        if (labels.isEmpty()) return 1;
        return (int) Math.ceil((double) labels.size() / getLabelsPerPage());
    }
    
    private void updatePageLabel() {
        int totalPages = getTotalPages();
        pageLabel.setText("Pagina " + (currentPage + 1) + " di " + totalPages);
    }
    
    private void saveToFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Salva lista etichette");
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }
            try {
                saveLabelsToFile(file);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Errore nel salvataggio del file: " + ex.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void saveLabelsToFile(File file) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        List<Label> labels = getLabels();
        
        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            
            if (!label.line1.isEmpty()) {
                writer.write(label.line1);
                writer.newLine();
            }
            
            if (!label.line2.isEmpty()) {
                writer.write(label.line2);
                writer.newLine();
            }
            
            // Aggiungi riga vuota tra etichette (tranne per l'ultima)
            if (i < labels.size() - 1) {
                writer.newLine();
            }
        }
        
        writer.close();
        
        JOptionPane.showMessageDialog(this,
            "Salvate " + labels.size() + " etichette",
            "Salvataggio completato", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void loadFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Seleziona file di testo");
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                loadLabelsFromFile(file);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Errore nella lettura del file: " + ex.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void loadLabelsFromFile(File file) throws IOException {
        tableModel.setRowCount(0);
        
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        String line1 = null;
        String line2 = null;
        
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                // Riga vuota - separa le etichette
                if (line1 != null || line2 != null) {
                    tableModel.addRow(new Object[]{
                        line1 != null ? line1 : "",
                        line2 != null ? line2 : ""
                    });
                    line1 = null;
                    line2 = null;
                }
            } else {
                // Riga con testo
                if (line1 == null) {
                    line1 = line.trim();
                } else if (line2 == null) {
                    line2 = line.trim();
                }
            }
        }
        
        // Aggiungi l'ultima etichetta se presente
        if (line1 != null || line2 != null) {
            tableModel.addRow(new Object[]{
                line1 != null ? line1 : "",
                line2 != null ? line2 : ""
            });
        }
        
        reader.close();
        currentPage = 0;
        updatePageLabel();
        updateTitle();
        previewPanel.repaint();
        
        JOptionPane.showMessageDialog(this,
            "Caricate " + tableModel.getRowCount() + " etichette",
            "Caricamento completato", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private List<Label> getLabels() {
        List<Label> labels = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String line1 = (String) tableModel.getValueAt(i, 0);
            String line2 = (String) tableModel.getValueAt(i, 1);
            if ((line1 != null && !line1.trim().isEmpty()) || 
                (line2 != null && !line2.trim().isEmpty())) {
                labels.add(new Label(
                    line1 != null ? line1 : "",
                    line2 != null ? line2 : ""
                ));
            }
        }
        return labels;
    }
    
    private Font getLabelFont(int size) {
        String fontFamily = (String) fontFamilyCombo.getSelectedItem();
        int style = boldCheckBox.isSelected() ? Font.BOLD : Font.PLAIN;
        return new Font(fontFamily, style, size);
    }
    
    private void printLabels(boolean onlyCurrentPage) {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pageFormat = job.defaultPage();
        Paper paper = pageFormat.getPaper();
        
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        
        if (onlyCurrentPage) {
            job.setPrintable(new SinglePagePrintable(currentPage), pageFormat);
        } else {
            job.setPrintable(new LabelPrintable(), pageFormat);
        }
        
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Errore durante la stampa: " + ex.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void drawLabel(Graphics2D g2, int x, int y, int width, int height, Label label, Font font) {
        // Disegna bordo etichetta
        g2.setColor(Color.BLACK);
        g2.drawRect(x, y, width, height);
        
        // Disegna testo
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        
        boolean hasLine1 = !label.line1.isEmpty();
        boolean hasLine2 = !label.line2.isEmpty();
        
        if (hasLine1 && hasLine2) {
            // Entrambe le righe - dividi lo spazio verticalmente
            int totalTextHeight = fm.getHeight() * 2;
            int startY = y + (height - totalTextHeight) / 2 + fm.getAscent();
            
            // Riga 1
            int textWidth1 = fm.stringWidth(label.line1);
            int textX1 = x + (width - textWidth1) / 2;
            g2.drawString(label.line1, textX1, startY);
            
            // Riga 2
            int textWidth2 = fm.stringWidth(label.line2);
            int textX2 = x + (width - textWidth2) / 2;
            g2.drawString(label.line2, textX2, startY + fm.getHeight());
            
        } else if (hasLine1) {
            // Solo riga 1 - centra verticalmente
            int textWidth = fm.stringWidth(label.line1);
            int textX = x + (width - textWidth) / 2;
            int textY = y + (height - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label.line1, textX, textY);
            
        } else if (hasLine2) {
            // Solo riga 2 - centra verticalmente
            int textWidth = fm.stringWidth(label.line2);
            int textX = x + (width - textWidth) / 2;
            int textY = y + (height - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label.line2, textX, textY);
        }
    }
    
    class PreviewPanel extends JPanel {
        
        public PreviewPanel() {
            // Dimensioni reali in millimetri convertite in pixel (assumendo 96 DPI standard)
            // 1 pollice = 25.4mm, quindi 1mm = 96/25.4 pixel
            double mmToPixel = 96.0 / 25.4;
            int width = (int) (A4_WIDTH_MM * mmToPixel);
            int height = (int) (A4_HEIGHT_MM * mmToPixel);
            setPreferredSize(new Dimension(width + 20, height + 20));
            setBackground(Color.LIGHT_GRAY);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Dimensioni reali in pixel
            double mmToPixel = 96.0 / 25.4;
            int pageWidth = (int) (A4_WIDTH_MM * mmToPixel);
            int pageHeight = (int) (A4_HEIGHT_MM * mmToPixel);
            
            // Disegna foglio A4
            g2.setColor(Color.WHITE);
            g2.fillRect(10, 10, pageWidth, pageHeight);
            
            // Disegna bordo foglio A4
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(10, 10, pageWidth, pageHeight);
            g2.setStroke(new BasicStroke(1));
            
            int labelWidth = (int) (getLabelWidth() * mmToPixel);
            int labelHeight = (int) (getLabelHeight() * mmToPixel);
            
            int cols = (int) (A4_WIDTH_MM / getLabelWidth());
            int rows = (int) (A4_HEIGHT_MM / getLabelHeight());
            
            List<Label> labels = getLabels();
            int labelsPerPage = cols * rows;
            int startIndex = currentPage * labelsPerPage;
            int endIndex = Math.min(startIndex + labelsPerPage, labels.size());
            
            int fontSize = (Integer) fontSizeSpinner.getValue();
            Font font = getLabelFont(fontSize);
            
            int labelIndex = startIndex;
            for (int row = 0; row < rows && labelIndex < endIndex; row++) {
                for (int col = 0; col < cols && labelIndex < endIndex; col++) {
                    int x = 10 + col * labelWidth;
                    int y = 10 + row * labelHeight;
                    
                    drawLabel(g2, x, y, labelWidth, labelHeight, labels.get(labelIndex), font);
                    labelIndex++;
                }
            }
        }
    }
    
    class SinglePagePrintable implements Printable {
        private int pageNum;
        
        public SinglePagePrintable(int pageNum) {
            this.pageNum = pageNum;
        }
        
        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int labelWidth = (int) (getLabelWidth() * MM_TO_POINTS);
            int labelHeight = (int) (getLabelHeight() * MM_TO_POINTS);
            
            int cols = (int) (A4_WIDTH_MM / getLabelWidth());
            int rows = (int) (A4_HEIGHT_MM / getLabelHeight());
            
            List<Label> labels = getLabels();
            int labelsPerPage = cols * rows;
            int startIndex = pageNum * labelsPerPage;
            int endIndex = Math.min(startIndex + labelsPerPage, labels.size());
            
            int fontSize = (Integer) fontSizeSpinner.getValue();
            Font font = getLabelFont(fontSize);
            
            int labelIndex = startIndex;
            for (int row = 0; row < rows && labelIndex < endIndex; row++) {
                for (int col = 0; col < cols && labelIndex < endIndex; col++) {
                    int x = col * labelWidth;
                    int y = row * labelHeight;
                    
                    drawLabel(g2, x, y, labelWidth, labelHeight, labels.get(labelIndex), font);
                    labelIndex++;
                }
            }
            
            return PAGE_EXISTS;
        }
    }
    
    class LabelPrintable implements Printable {
        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            List<Label> labels = getLabels();
            int labelsPerPage = getLabelsPerPage();
            int totalPages = getTotalPages();
            
            if (pageIndex >= totalPages) return NO_SUCH_PAGE;
            
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int labelWidth = (int) (getLabelWidth() * MM_TO_POINTS);
            int labelHeight = (int) (getLabelHeight() * MM_TO_POINTS);
            
            int cols = (int) (A4_WIDTH_MM / getLabelWidth());
            int rows = (int) (A4_HEIGHT_MM / getLabelHeight());
            
            int startIndex = pageIndex * labelsPerPage;
            int endIndex = Math.min(startIndex + labelsPerPage, labels.size());
            
            int fontSize = (Integer) fontSizeSpinner.getValue();
            Font font = getLabelFont(fontSize);
            
            int labelIndex = startIndex;
            for (int row = 0; row < rows && labelIndex < endIndex; row++) {
                for (int col = 0; col < cols && labelIndex < endIndex; col++) {
                    int x = col * labelWidth;
                    int y = row * labelHeight;
                    
                    drawLabel(g2, x, y, labelWidth, labelHeight, labels.get(labelIndex), font);
                    labelIndex++;
                }
            }
            
            return PAGE_EXISTS;
        }
    }
    
    static class Label {
        String line1;
        String line2;
        
        Label(String line1, String line2) {
            this.line1 = line1;
            this.line2 = line2;
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e) {
                e.printStackTrace();
            }
            new LabelPrinter().setVisible(true);
        });
    }
}