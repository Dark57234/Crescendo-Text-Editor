import javax.swing.*;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PrinterException;
import java.io.File;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.time.Duration;

/**
 * Author: Sophie, Anton, Franklin, John
 * Last Edited: 10/11/26
 */
public class CrescendoTextEditor {

    private static final DecimalFormat formatter = new DecimalFormat("00");
    private static long configuredHours = 0;
    private static long configuredMinutes = 25;
    private static long configuredSeconds = 0;

    
    private static JFrame frame;
    private static JTextPane textPane;
    //Records every edit for our little undo button
    private static final UndoManager undo = new UndoManager();
    //The default format for our document
    private static final JButton templateButton = new JButton("Default  |  Times New Roman 12pt");
    private static String templateName = "Default";
    private static final JLabel posLabel = new JLabel("Ln 1, Col 1");
    private static final JLabel charLabel = new JLabel("0 characters");
    private static JComboBox<Integer> sizeBox; // the font size dropdown in the toolbar
    private static JComboBox<String> fontBox; // the font dropdown in the toolbar
    private static boolean updatingToolbar = false; // true while WE change the dropdown (not the user)

    private static String font = "Times New Roman";
    private static int size = 12;
    private static float spacing = 0f;

    private static String headerText = "";
    private static String footerText = "";
    private static boolean pageNumbers = false;

    public static void main(String[] args) {
        /* Makes sure all UI code runs on Swing's event thread. Prevents threading bugs :D */
        SwingUtilities.invokeLater(CrescendoTextEditor::createAndShowGui);
    }


    private static void createAndShowGui() {
        frame = new JFrame("Text Editor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);

        textPane = new JTextPane();
        textPane.setMargin(new Insets(8, 8, 8, 8));
        textPane.getDocument().addUndoableEditListener(e -> undo.addEdit(e.getEdit()));
        textPane.addCaretListener(e -> updateStatus());
        textPane.registerKeyboardAction(e -> doUndo(), KeyStroke.getKeyStroke("ctrl Z"), JComponent.WHEN_FOCUSED);
        textPane.registerKeyboardAction(e -> doRedo(), KeyStroke.getKeyStroke("ctrl Y"), JComponent.WHEN_FOCUSED);
        reapplyFormat();
        undo.discardAllEdits();

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        // Bottom: toolbar + New Note button, then the status bar
        JButton noteButton = button("New Note", e -> newNote());
        JPanel toolRow = new JPanel(new BorderLayout());
        toolRow.add(createToolbar(), BorderLayout.CENTER);
        toolRow.add(noteButton, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.add(toolRow, BorderLayout.NORTH);
        south.add(createStatusBar(), BorderLayout.SOUTH);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(createTopBar(frame), BorderLayout.NORTH);
        editorPanel.add(scroll, BorderLayout.CENTER);
        editorPanel.add(south, BorderLayout.SOUTH);

        JPanel background = new JPanel(new BorderLayout());
        background.setBackground(new Color(10, 54, 30));
        background.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        background.add(editorPanel, BorderLayout.CENTER);

        frame.setContentPane(background);
        frame.setVisible(true);
        textPane.requestFocusInWindow();
    }


    private static JMenuBar createTopBar(JFrame parentFrame) {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(Color.WHITE);

        // File
        JMenu fileMenu = new JMenu("File");
        add(fileMenu, "New", e -> newDocument());
        add(fileMenu, "Open", e -> openFile());
        add(fileMenu, "Export", e -> exportFile());
        add(fileMenu, "Print", e -> printDocument());

        // Edit
        JMenu editMenu = new JMenu("Edit");
        add(editMenu, "Search", e -> search());
        add(editMenu, "Replace", e -> replace());
        add(editMenu, "Search and Replace", e -> replace());
        add(editMenu, "Copy", e -> textPane.copy());
        add(editMenu, "Paste", e -> textPane.paste());
        add(editMenu, "Select All", e -> textPane.selectAll());

        // Templates
        JMenu templatesMenu = new JMenu("Templates");
        add(templatesMenu, "MLA Format", e -> applyTemplate("MLA", "Times New Roman", 12, 1f));
        add(templatesMenu, "APA Format", e -> applyTemplate("APA", "Times New Roman", 12, 1f));
        add(templatesMenu, "Custom Formats", e -> customTemplate());

        // Format
        JMenu formatMenu = new JMenu("Format");

        JMenu textFormat = new JMenu("Text format");
        add(textFormat, "Bold", e -> toggle("bold"));
        add(textFormat, "Italics", e -> toggle("italic"));
        add(textFormat, "Underline", e -> toggle("underline"));
        add(textFormat, "Strikethrough", e -> toggle("strike"));
        formatMenu.add(textFormat);

        JMenu spacingMenu = new JMenu("Line spacing");
        add(spacingMenu, "Single", e -> setSpacing(0f));
        add(spacingMenu, "1.15", e -> setSpacing(0.15f));
        add(spacingMenu, "1.5", e -> setSpacing(0.5f));
        add(spacingMenu, "Double", e -> setSpacing(1f));
        formatMenu.add(spacingMenu);

        JCheckBoxMenuItem pageNumItem = new JCheckBoxMenuItem("Page Numbers");
        pageNumItem.addActionListener(e -> pageNumbers = pageNumItem.isSelected());
        formatMenu.add(pageNumItem);

        add(formatMenu, "Headers", e -> {
            String s = JOptionPane.showInputDialog(frame, "Header text:", headerText);
            if (s != null) headerText = s;
        });
        add(formatMenu, "Footers", e -> {
            String s = JOptionPane.showInputDialog(frame, "Footer text:", footerText);
            if (s != null) footerText = s;
        });

        // Pomodoro Menu with "Start" and "Settings" options
        JMenu pomodoroMenu = new JMenu("Pomodoro");
        JMenuItem startItem = new JMenuItem("Start");
        JMenuItem settingsItem = new JMenuItem("Settings");
        // Action for Start: Opens non-modal popup with *only* the countdown timer that auto-closes on finish
        startItem.addActionListener(e -> openTimerPopup(parentFrame));
        // Action for Settings: Opens configuration popup to adjust time
        settingsItem.addActionListener(e -> openSettingsPopup(parentFrame));
        pomodoroMenu.add(startItem);
        pomodoroMenu.add(settingsItem);

        // Music (nothing here cause idk how to build)
        JMenu musicMenu = new JMenu("Music \u25B6");
        musicMenu.setEnabled(false);

        // Clicking this sets the WHOLE document to the template's font and size
        templateButton.setFont(new Font("Arial", Font.BOLD, 12));
        templateButton.setFocusable(false);
        templateButton.setBorderPainted(false);
        templateButton.setContentAreaFilled(false);
        templateButton.setToolTipText("Click to set the whole document to this font and size");
        templateButton.addActionListener(e -> applyTemplate(templateName, font, size, spacing));

        bar.add(fileMenu);
        bar.add(editMenu);
        bar.add(templatesMenu);
        bar.add(formatMenu);
        bar.add(Box.createHorizontalGlue());
        bar.add(templateButton);
        bar.add(Box.createHorizontalGlue());
        bar.add(pomodoroMenu);
        bar.add(musicMenu);

        return bar;
    }

    /** Makes a menu item, hooks up its action, and adds it to the menu. */
    private static void add(JMenu menu, String text, ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(action);
        menu.add(item);
    }

    private static void newDocument() {
        int answer = JOptionPane.showConfirmDialog(frame, "Start a new document? Unexported text will be lost. :( ",
                "New", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        textPane.setText("");
        reapplyFormat();
        undo.discardAllEdits();
    }

    private static void openFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            textPane.setText(Files.readString(chooser.getSelectedFile().toPath()));
            reapplyFormat();
            undo.discardAllEdits();
            textPane.setCaretPosition(0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Uh Oh! Could not open file: " + ex.getMessage());
        }
    }

    private static void exportFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().contains(".")) file = new File(file.getPath() + ".txt");
        try {
            Files.writeString(file.toPath(), allText());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Uh Oh! Could not export: " + ex.getMessage());
        }
    }

    private static void printDocument() {
        MessageFormat header = headerText.isEmpty() ? null : new MessageFormat(esc(headerText));
        String foot = esc(footerText) + (pageNumbers ? "  Page {0}" : ""); // {0} = page number
        MessageFormat footer = foot.isEmpty() ? null : new MessageFormat(foot);
        try {
            textPane.print(header, footer);
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(frame, "Uh Oh! Printing failed: " + ex.getMessage());
        }
    }

    /** Stops MessageFormat from choking on ' and { typed by the user. */
    private static String esc(String s) {
        return s.replace("'", "''").replace("{", "'{'");
    }


    private static String allText() {
        try {
            return textPane.getDocument().getText(0, textPane.getDocument().getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    private static void search() {
        String query = JOptionPane.showInputDialog(frame, "Search for:");
        if (query == null || query.isEmpty()) return;
        String text = allText();
        int found = text.indexOf(query, textPane.getSelectionEnd()); // look after the cursor first
        if (found < 0) found = text.indexOf(query);                  // then wrap to the top
        if (found < 0) {
            JOptionPane.showMessageDialog(frame, "Aw Shucks... Not found.");
            return;
        }
        textPane.requestFocusInWindow();
        textPane.select(found, found + query.length());
    }

    private static void replace() {
        String find = JOptionPane.showInputDialog(frame, "Find:");
        if (find == null || find.isEmpty()) return;
        String with = JOptionPane.showInputDialog(frame, "Replace with:");
        if (with == null) return;

        String text = allText();
        StyledDocument doc = textPane.getStyledDocument();
        try {
            // Go from the END to the START so earlier positions don't shift
            int i = text.lastIndexOf(find);
            while (i >= 0) {
                doc.remove(i, find.length());
                doc.insertString(i, with, null);
                i = text.lastIndexOf(find, i - find.length());
            }
        } catch (BadLocationException ex) {
            // ignore
        }
    }


    private static void applyTemplate(String name, String newFont, int newSize, float newSpacing) {
        templateName = name;
        font = newFont;
        size = newSize;
        spacing = newSpacing;
        templateButton.setText(name + "  |  " + font + " " + size + "pt");
        reapplyFormat();

        // Make the toolbar show the template's font and size too
        if (fontBox != null) {
            updatingToolbar = true;          // so this doesn't count as the user picking a font
            fontBox.setSelectedItem(font);
            updatingToolbar = false;
        }
        if (sizeBox != null) {
            updatingToolbar = true;
            sizeBox.setSelectedItem(size);
            updatingToolbar = false;
        }
    }

    private static void customTemplate() {
        JComboBox<String> fonts = new JComboBox<>(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        fonts.setSelectedItem(font);
        JSpinner sizeBoxSpinner = new JSpinner(new SpinnerNumberModel(size, 6, 96, 1));
        JComboBox<String> spacingBox = new JComboBox<>(new String[]{"Single", "1.15", "1.5", "Double"});
        float[] values = {0f, 0.15f, 0.5f, 1f};

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Font:"));         panel.add(fonts);
        panel.add(new JLabel("Size:"));         panel.add(sizeBoxSpinner);
        panel.add(new JLabel("Line spacing:")); panel.add(spacingBox);

        int answer = JOptionPane.showConfirmDialog(frame, panel, "Custom Format", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;

        applyTemplate("Custom", (String) fonts.getSelectedItem(), (Integer) sizeBoxSpinner.getValue(),
                values[spacingBox.getSelectedIndex()]);
    }

    /** Applies font / size / spacing to the whole document and to anything typed next. */
    private static void reapplyFormat() {
        SimpleAttributeSet chars = new SimpleAttributeSet();
        StyleConstants.setFontFamily(chars, font);
        StyleConstants.setFontSize(chars, size);
        SimpleAttributeSet para = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(para, spacing);

        StyledDocument doc = textPane.getStyledDocument();
        doc.setCharacterAttributes(0, doc.getLength(), chars, false);
        doc.setParagraphAttributes(0, doc.getLength(), para, false);
        textPane.getInputAttributes().addAttributes(chars);
    }


    private static void style(SimpleAttributeSet s) {
        textPane.setCharacterAttributes(s, false); // selected text, or whatever you type next
        textPane.requestFocusInWindow();
    }

    private static void toggle(String kind) {
        AttributeSet now = textPane.getCharacterAttributes();
        SimpleAttributeSet s = new SimpleAttributeSet();
        switch (kind) {
            case "bold":      StyleConstants.setBold(s, !StyleConstants.isBold(now)); break;
            case "italic":    StyleConstants.setItalic(s, !StyleConstants.isItalic(now)); break;
            case "underline": StyleConstants.setUnderline(s, !StyleConstants.isUnderline(now)); break;
            case "strike":    StyleConstants.setStrikeThrough(s, !StyleConstants.isStrikeThrough(now)); break;
        }
        style(s);
    }

    private static void applyFont(String name) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setFontFamily(s, name);
        style(s);
    }

    private static void setSize(int newSize) {
        newSize = Math.max(1, Math.min(200, newSize));
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setFontSize(s, newSize);
        style(s);
        if (sizeBox != null) {
            updatingToolbar = true;
            sizeBox.setSelectedItem(newSize);
            updatingToolbar = false;
        }
    }

    private static void pickColor() {
        Color c = JColorChooser.showDialog(frame, "Text Color", Color.BLACK);
        if (c == null) return;
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        style(s);
    }

    private static void setSpacing(float value) {
        spacing = value;
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(s, value);
        textPane.setParagraphAttributes(s, false); // selected paragraphs, or the current one
        textPane.requestFocusInWindow();
    }

    private static void addBullets() {
        StyledDocument doc = textPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int first = root.getElementIndex(textPane.getSelectionStart());
        int last = root.getElementIndex(textPane.getSelectionEnd());
        try {
            for (int i = last; i >= first; i--) { // bottom to top so positions don't shift
                doc.insertString(root.getElement(i).getStartOffset(), "\u2022 ", null);
            }
        } catch (BadLocationException ex) {
            // ignore
        }
        textPane.requestFocusInWindow();
    }

    private static void doUndo() {
        if (undo.canUndo()) undo.undo();
        textPane.requestFocusInWindow();
    }

    private static void doRedo() {
        if (undo.canRedo()) undo.redo();
        textPane.requestFocusInWindow();
    }


    private static JPanel createToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));

        panel.add(button("Undo", e -> doUndo()));
        panel.add(button("Redo", e -> doRedo()));

        fontBox = new JComboBox<>(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        fontBox.setSelectedItem(font);
        fontBox.setFocusable(false);
        fontBox.addActionListener(e -> {
            if (!updatingToolbar) applyFont((String) fontBox.getSelectedItem());
        });
        panel.add(fontBox);

        // Font Size Dropdown (Replaces bugged -, textfield, + buttons)
        Integer[] standardSizes = {8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72};
        sizeBox = new JComboBox<>(standardSizes);
        sizeBox.setSelectedItem(size);
        sizeBox.setFocusable(false);
        sizeBox.addActionListener(e -> {
            if (!updatingToolbar && sizeBox.getSelectedItem() != null) {
                setSize((Integer) sizeBox.getSelectedItem());
            }
        });
        panel.add(sizeBox);

        panel.add(button("B", e -> toggle("bold")));
        panel.add(button("I", e -> toggle("italic")));
        panel.add(button("U", e -> toggle("underline")));
        panel.add(button("Color", e -> pickColor()));
        panel.add(button("\u2022 List", e -> addBullets()));
        return panel;
    }

    /** setFocusable(false) keeps the cursor in the text area when you click the button. */
    private static JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(action);
        return b;
    }

    private static void newNote() {
        JDialog dialog = new JDialog(frame, "Note", false);
        JTextArea area = new JTextArea(8, 20);
        area.setLineWrap(true);
        area.setBackground(new Color(255, 249, 196));
        dialog.add(new JScrollPane(area));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(35, 35, 35));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 4));
        left.setOpaque(false);
        posLabel.setForeground(Color.LIGHT_GRAY);
        charLabel.setForeground(Color.LIGHT_GRAY);
        left.add(posLabel);
        left.add(charLabel);

        JButton help = button("Help", e -> JOptionPane.showMessageDialog(frame,
                "Ctrl+Z = Undo, Ctrl+Y = Redo\n"
                + "Pomodoro > Settings to set the timer, then Pomodoro > Start.\n"
                + "New Note opens a sticky note you can type in."));

        bar.add(left, BorderLayout.WEST);
        bar.add(help, BorderLayout.EAST);
        return bar;
    }

    private static void updateStatus() {
        int pos = textPane.getCaretPosition();
        Element root = textPane.getDocument().getDefaultRootElement();
        int line = root.getElementIndex(pos);
        int col = pos - root.getElement(line).getStartOffset();
        posLabel.setText("Ln " + (line + 1) + ", Col " + (col + 1));
        charLabel.setText(textPane.getDocument().getLength() + " characters");
        
        if (sizeBox != null) { 
            // Look up the font size from the actual document text element at the caret, 
            // falling back to character/input attributes if needed.
            int currentSize = size;
            try {
                StyledDocument doc = textPane.getStyledDocument();
                Element element = doc.getCharacterElement(Math.max(0, pos - 1));
                if (element != null) {
                    currentSize = StyleConstants.getFontSize(element.getAttributes());
                }
            } catch (Exception e) {
                currentSize = StyleConstants.getFontSize(textPane.getCharacterAttributes());
            }
            
            updatingToolbar = true;
            sizeBox.setSelectedItem(currentSize);
            updatingToolbar = false;
        }
    }


    private static void openTimerPopup(JFrame parentFrame) {
        // 'false' makes the dialog non-modal so you can type in the editor while it runs
        JDialog timerDialog = new JDialog(parentFrame, "Pomodoro Countdown", false);
        timerDialog.setSize(280, 150);
        timerDialog.setLocationRelativeTo(parentFrame);
        timerDialog.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(245, 245, 245));

        JLabel timerDisplay = new JLabel("00 : 00 : 00");
        timerDisplay.setFont(new Font("Arial", Font.BOLD, 26));
        panel.add(timerDisplay);

        timerDialog.setContentPane(panel);


        final long initialTimeLeft = (configuredHours * 3600000L) + (configuredMinutes * 60000L) + (configuredSeconds * 1000L);
        if (initialTimeLeft <= 0) {
            JOptionPane.showMessageDialog(parentFrame, "Please set a timer duration greater than 0 in Settings first!", "Timer Not Set", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Start countdown timer thread
        Timer countdownTimer = new Timer(1000, null);
        final long[] timeLeftRef = {initialTimeLeft};

        countdownTimer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timeLeftRef[0] -= 1000;
                if (timeLeftRef[0] <= 0) {
                    timeLeftRef[0] = 0;
                    countdownTimer.stop();
                    Toolkit.getDefaultToolkit().beep();
                    timerDialog.dispose(); // Automatically close popup when finished
                }
                updateLabel(timeLeftRef[0], timerDisplay);
            }
        });

        // Initialize display immediately
        updateLabel(initialTimeLeft, timerDisplay);
        countdownTimer.start();

        timerDialog.setVisible(true);
    }

    private static void updateLabel(long timeLeft, JLabel label) {
        Duration duration = Duration.ofMillis(timeLeft);
        long h = duration.toHours();
        duration = duration.minusHours(h);
        long m = duration.toMinutes();
        duration = duration.minusMinutes(m);
        long s = duration.toMillis() / 1000;

        label.setText(formatter.format(h) + " : " + formatter.format(m) + " : " + formatter.format(s));
    }

    private static void openSettingsPopup(JFrame parentFrame) {
        JDialog settingsDialog = new JDialog(parentFrame, "Pomodoro Settings", true);
        settingsDialog.setSize(340, 200);
        settingsDialog.setLocationRelativeTo(parentFrame);
        settingsDialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new Color(245, 245, 245));

        TimerRow timerRow = setTimerPanel(panel, 30, 45, "Configure Work Time");

        // Set current values onto boxes
        timerRow.hourBox.setSelectedItem(formatter.format(configuredHours));
        timerRow.minuteBox.setSelectedItem(formatter.format(configuredMinutes));
        timerRow.secondBox.setSelectedItem(formatter.format(configuredSeconds));

        JButton saveButton = new JButton("Save");
        saveButton.setFont(new Font("Arial", Font.BOLD, 12));
        saveButton.setBounds(120, 110, 80, 30);
        saveButton.addActionListener(e -> {
            configuredHours = Long.parseLong(timerRow.hourBox.getSelectedItem().toString());
            configuredMinutes = Long.parseLong(timerRow.minuteBox.getSelectedItem().toString());
            configuredSeconds = Long.parseLong(timerRow.secondBox.getSelectedItem().toString());
            settingsDialog.dispose();
        });
        panel.add(saveButton);

        settingsDialog.setContentPane(panel);
        settingsDialog.setVisible(true);
    }

    public static class TimerRow {
        public JComboBox<String> hourBox;
        public JComboBox<String> minuteBox;
        public JComboBox<String> secondBox;

        public TimerRow(JComboBox<String> h, JComboBox<String> m, JComboBox<String> s) {
            this.hourBox = h;
            this.minuteBox = m;
            this.secondBox = s;
        }
    }

    private static TimerRow setTimerPanel(JPanel panel, int x, int y, String labelText) {
        JLabel label = new JLabel(labelText);
        label.setBounds(x, y - 25, 250, 20);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(label);

        // Hour ComboBox
        JComboBox<String> hourComboBox = new JComboBox<>();
        for (int i = 0; i <= 24; i++) {
            hourComboBox.addItem(formatter.format(i));
        }
        hourComboBox.setBounds(x, y, 55, 35);
        hourComboBox.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(hourComboBox);

        // Minutes ComboBox
        JComboBox<String> minutesComboBox = new JComboBox<>();
        for (int i = 0; i < 60; i++) {
            minutesComboBox.addItem(formatter.format(i));
        }
        minutesComboBox.setBounds(x + 65, y, 55, 35);
        minutesComboBox.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(minutesComboBox);

        // Seconds ComboBox
        JComboBox<String> secondsComboBox = new JComboBox<>();
        for (int i = 0; i < 60; i++) {
            secondsComboBox.addItem(formatter.format(i));
        }
        secondsComboBox.setBounds(x + 130, y, 55, 35);
        secondsComboBox.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(secondsComboBox);

        // Labels (h, min, sec)
        JLabel h = new JLabel("h");
        h.setBounds(x + 22, y + 36, 20, 15);
        panel.add(h);

        JLabel min = new JLabel("min");
        min.setBounds(x + 80, y + 36, 30, 15);
        panel.add(min);

        JLabel sec = new JLabel("sec");
        sec.setBounds(x + 145, y + 36, 30, 15);
        panel.add(sec);

        return new TimerRow(hourComboBox, minutesComboBox, secondsComboBox);
    }
}