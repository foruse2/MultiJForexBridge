package com.ttorhcs.multijforexbridge;

import com.dukascopy.api.*;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

@RequiresFullAccess
@Library("jForexBridge.jar;jna-3.3.0.jar;jna-3.3.0-platform.jar")
public class MultiJForexBridge implements IStrategy {

    List<JForexFstBridge> strategiesList = new ArrayList<JForexFstBridge>();
    //define variables for GUI
    JTable table;
    JFrame frame;
    JScrollPane scrollPane;
    MyTableModel tableModel;
    final String TAB_KEY = "MultiJForexBridge Configuration";
    boolean onStartStarted = false;
    //strategy variables
    IContext context;
    IConsole console;
    IUserInterface userInterface;
    /*
     * At the main bridge's startup there will be properties indicating 
     * several options.
     */
    public File brigdesFile, optionsFile;
    public int equityStop = 0;

    @Override
    public void onStart(final IContext context) throws JFException {

        this.context = context;
        this.console = context.getConsole();
        this.userInterface = context.getUserInterface();
        List<Boolean> startedList = new ArrayList<Boolean>();
        brigdesFile = new File(getPath("config") + "\\bridges.xml");
        optionsFile = new File(getPath("config") + "\\options.xml");
        if (brigdesFile != null && !"".equals(brigdesFile.getName()) && brigdesFile.isFile()
                && (brigdesFile.getName().endsWith(".xml") || brigdesFile.getName().endsWith(".XML"))
                && (brigdesFile.length() != 0)) {
            //read strategies and add to the list
            startedList = readFromXML();
        } else {
            strategiesList.add(new JForexFstBridge());
            startedList.add(false);
        }
        saveConfig();

        tableModel = new MyTableModel();
        table = new JTable(tableModel);
        table.setPreferredScrollableViewportSize(new Dimension(600, 70));
        table.setFillsViewportHeight(true);
        scrollPane = new JScrollPane(table);

        try {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    MainGui myPanel = new MainGui();
                    Container content = myPanel.createAndShowGUI();

                    frame = new JFrame(TAB_KEY);
                    frame.setContentPane(content);
                    frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    frame.pack();
                    //get the dimensions of the screen
                    Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
                    frame.setSize(850, 400);
                    // Determine the new location of the window                            
                    int x = (dim.width) / 2 - frame.getWidth() / 2;
                    int y = (dim.height) / 2 - frame.getHeight() / 2;
                    frame.setVisible(true);
                    frame.setLocation(x, y);

                }
            });
        } catch (Exception exception) {
            log(exception);
        }

        /*
         * start bridges if flagged as started
         */
        try {
            for (int i = 0; i < strategiesList.size(); i++) {
                if (startedList.get(i)) {
                    JForexFstBridge bridge = strategiesList.get(i);
                    bridge.onStart(context);
                }
            }
            onStartStarted = true;
        } catch (Exception exception) {
            log(exception);
        }



    }//end of the onStart method

    private void log(String message) {
        console.getOut().println(message);
    }

    private void log(Exception e) {
        e.printStackTrace(console.getErr());
    }

    /*
     * method, which reads xml file by using jaxp-dom technique. 
     * Method creates a new Bridge object and then replaces the default values with
     * values of xml file.
     */
    private List<Boolean> readFromXML() {
        List<Boolean> startedList = new ArrayList<Boolean>();
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(brigdesFile);
            document.getDocumentElement().normalize();
            NodeList strategyElementList = document.getElementsByTagName("Strategy");

            //for each bridge Node add new Strategy to bridge list and get NodeList of elements and iterate through
            for (int x = 0; x < strategyElementList.getLength(); x++) {
                //add new bridge to list
                JForexFstBridge newBridge = new JForexFstBridge();
                strategiesList.add(newBridge);
                //get child elements:
                NodeList strategyNodeList = strategyElementList.item(x).getChildNodes();

                for (int y = 0; y < strategyNodeList.getLength(); y++) {
                    if (strategyNodeList.item(y).getNodeType() == Node.ELEMENT_NODE) {
                        Element parameterElement = (Element) strategyNodeList.item(y);
                        String variableName = parameterElement.getNodeName();
                        String nodeValue = parameterElement.getTextContent();

                        Field field = newBridge.getClass().getField(variableName);
                        Class<?> clazz = field.getType();
                        if (variableName.equals("started")) {
                            startedList.add(Boolean.parseBoolean(nodeValue));
                        } else if (field.getType().isEnum()) {
                            int valueEnum = Integer.valueOf(nodeValue);
                            Object[] enums = field.getType().getEnumConstants();
                            field.set(newBridge, enums[valueEnum]);
                        } else if (field.getType() == Period.class) {
                            Period newPeriod = Period.valueOf(nodeValue);
                            field.set(newBridge, newPeriod);
                        } else if (field.getType() == double.class) {
                            double newDouble = Double.parseDouble(nodeValue);
                            field.set(newBridge, newDouble);
                        } else if (field.getType() == boolean.class) {
                            boolean newBoolean = Boolean.parseBoolean(nodeValue);
                            field.set(newBridge, newBoolean);
                        } else if (field.getType() == String.class) {
                            field.set(newBridge, nodeValue);
                        } else if (field.getType() == int.class) {
                            int newInt = Integer.parseInt(nodeValue);
                            field.set(newBridge, newInt);
                        } else {
                            throw new IllegalArgumentException("Parameter's type not supported. Required "
                                    + "Instrument/Period/OfferSide/String/boolean/double/int, found: "
                                    + newBridge.getClass().getField(variableName).getType());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log(e);
            log("invalid config file: " + brigdesFile.getName());
            strategiesList.clear();
            strategiesList.add(new JForexFstBridge());
            startedList.add(false);
        }
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(optionsFile);
            document.getDocumentElement().normalize();
            NodeList optionElementsList = document.getElementsByTagName("Option");
            for (int x = 0; x < optionElementsList.getLength(); x++) {
                NodeList optionNodeList = optionElementsList.item(x).getChildNodes();
                for (int y = 0; y < optionNodeList.getLength(); y++) {
                    if (optionNodeList.item(y).getNodeType() == Node.ELEMENT_NODE) {
                        Element parameterElement = (Element) optionNodeList.item(y);
                        String variableName = parameterElement.getNodeName();
                        String nodeValue = parameterElement.getTextContent();

                        if (variableName.equals("equityStop")) {
                            equityStop = Integer.parseInt(nodeValue);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log(e);
            log("invalid options file: " + optionsFile.getName());
            equityStop = 0;
        }

        if (strategiesList.isEmpty()){
            strategiesList.add(new JForexFstBridge());
            startedList.add(false);
        }
        
        return startedList;
    }//end of method readFromXML

    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (!onStartStarted) {
            return;
        }
        for (JForexFstBridge bridge : strategiesList) {
            if (bridge.instrument == instrument && bridge.started) {
                bridge.onTick(instrument, tick);
            }
        }
        if (context.getAccount().getEquity() < equityStop) {
            for (JForexFstBridge bridge : strategiesList) {
                if (bridge.started) {
                    bridge.onStop();
                }
            }
            for (IOrder order : context.getEngine().getOrders()) {
                order.close();
            }
            saveConfig();
        }
    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!onStartStarted) {
            return;
        }
        for (JForexFstBridge bridge : strategiesList) {
            if (bridge.period == period && bridge.started) {
                bridge.onBar(instrument, period, askBar, bidBar);
            }
        }

    }

    @Override
    public void onMessage(IMessage message) throws JFException {
        if (!onStartStarted) {
            return;
        }
        for (JForexFstBridge bridge : strategiesList) {
            if (bridge.started) {
                bridge.onMessage(message);
            }
        }
    }

    @Override
    public void onAccount(IAccount account) throws JFException {
        if (!onStartStarted) {
            return;
        }
        for (JForexFstBridge bridge : strategiesList) {
            if (bridge.started) {
                bridge.onAccount(account);
            }
        }
    }

    //close the tab/frame when bridge is stopped
    @Override
    public void onStop() throws JFException {
        if (!onStartStarted) {
            return;
        }
        saveConfig();
        for (IStrategy strategy : strategiesList) {
            strategy.onStop();
        }
        userInterface.removeBottomTab(TAB_KEY);
        if (frame != null) {
            frame.setVisible(false);
        }
    }

    /*
     * A class which creates a content pane for 
     * tab/frame. Here are defined all elements and actions.
     */
    private class MainGui extends javax.swing.JPanel {

        /**
         * Creates new form multiBridge
         */
        /**
         * This method is called from within the constructor to initialize the
         * form. WARNING: Do NOT modify this code. The content of this method is
         * always regenerated by the Form Editor.
         */
        @SuppressWarnings("unchecked")
        // <editor-fold defaultstate="collapsed" desc="Generated Code">
        private JPanel createAndShowGUI() {
            java.awt.GridBagConstraints gridBagConstraints;

            ButtonPanel = new javax.swing.JPanel();
            startButton = new javax.swing.JButton();
            stopButton = new javax.swing.JButton();
            addButton = new javax.swing.JButton();
            removeButton = new javax.swing.JButton();
            startAllButton = new javax.swing.JButton();
            stopAllButton = new javax.swing.JButton();
            eqStopPanel = new javax.swing.JPanel();
            jLabel1 = new javax.swing.JLabel();
            eqStopTextField = new javax.swing.JTextField(15);
            setEqStopButton = new javax.swing.JButton();

            setPreferredSize(new java.awt.Dimension(700, 400));

            ButtonPanel.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

            startButton.setText("Start selected");

            stopButton.setText("Stop selected");

            addButton.setText("Add new Bridge");

            removeButton.setText("Remove selected");

            startAllButton.setText("Start all");

            stopAllButton.setText("Stop all");

            addButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    strategiesList.add(new JForexFstBridge());
                    tableModel.fireTableChanged(new TableModelEvent(tableModel));
                }
            });

            startAllButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        for (JForexFstBridge bridge : strategiesList) {
                            if (!bridge.started) {
                                try {
                                    bridge.onStart(context);
                                } catch (JFException ex) {
                                    log(ex);
                                }
                            }
                        }
                        onStartStarted = true;

                    } catch (Exception exception) {
                        log(exception);
                    }
                    if (onStartStarted) {
                        console.getNotif().println("Strategy/ies started!");
                    } else {
                        console.getErr().println("Error while starting strategies!");
                    }
                    saveConfig();
                }
            });

            removeButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] selectedRows = table.getSelectedRows();
                    Collection<IStrategy> collectionToRemove = new ArrayList<IStrategy>();
                    for (int i : selectedRows) {
                        collectionToRemove.add(strategiesList.get(i));
                    }
                    strategiesList.removeAll(collectionToRemove);
                    tableModel.fireTableChanged(new TableModelEvent(tableModel));
                    saveConfig();
                }
            });

            startButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] selectedRows = table.getSelectedRows();

                    for (int i : selectedRows) {
                        JForexFstBridge bridge = strategiesList.get(i);
                        if (!bridge.started) {
                            try {
                                bridge.onStart(context);
                            } catch (JFException ex) {
                                log(ex);
                            }
                        }
                    }
                    tableModel.fireTableChanged(new TableModelEvent(tableModel));
                    saveConfig();
                }
            });

            stopButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] selectedRows = table.getSelectedRows();
                    for (int i : selectedRows) {
                        JForexFstBridge bridge = strategiesList.get(i);
                        if (bridge.started) {
                            try {
                                bridge.onStop();
                            } catch (JFException ex) {
                                log(ex);
                            }
                        }
                    }
                    tableModel.fireTableChanged(new TableModelEvent(tableModel));
                    saveConfig();
                }
            });

            startAllButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for (int i = 0; i < strategiesList.size(); i++) {
                        JForexFstBridge bridge = strategiesList.get(i);
                        if (!bridge.started) {
                            try {
                                bridge.onStart(context);
                            } catch (JFException ex) {
                                log(ex);
                            }
                        }

                    }
                    tableModel.fireTableChanged(new TableModelEvent(tableModel));
                    saveConfig();
                }
            });

            stopAllButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for (int i = 0; i < strategiesList.size(); i++) {
                        JForexFstBridge bridge = strategiesList.get(i);
                        try {
                            if (bridge.started) {
                                bridge.onStop();
                            }
                        } catch (JFException ex) {
                            log(ex);
                        }
                    }
                    tableModel.fireTableChanged(new TableModelEvent(tableModel));
                    saveConfig();
                }
            });
            setEqStopButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int eqStopFromText = 0;
                    try {
                        eqStopFromText = Integer.parseInt(eqStopTextField.getText());
                        equityStop = eqStopFromText;
                    } catch (Exception ex) {
                        log(ex);
                        eqStopTextField.setText(equityStop + "");
                    }
                    saveConfig();
                }
            });

            this.setUpColumns();

            javax.swing.GroupLayout ButtonPanelLayout = new javax.swing.GroupLayout(ButtonPanel);
            ButtonPanel.setLayout(ButtonPanelLayout);
            ButtonPanelLayout.setHorizontalGroup(
                    ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, ButtonPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(startButton)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(stopButton)
                    .addGap(51, 51, 51)
                    .addComponent(addButton)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(removeButton)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 65, Short.MAX_VALUE)
                    .addComponent(startAllButton)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(stopAllButton)
                    .addGap(8, 8, 8)));
            ButtonPanelLayout.setVerticalGroup(
                    ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ButtonPanelLayout.createSequentialGroup()
                    .addGap(4, 4, 4)
                    .addGroup(ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(stopButton)
                    .addComponent(startButton)
                    .addComponent(addButton)
                    .addComponent(removeButton))
                    .addGroup(ButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(startAllButton)
                    .addComponent(stopAllButton)))
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

            eqStopPanel.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
            eqStopPanel.setLayout(new java.awt.GridBagLayout());

            jLabel1.setText("Equity Stop");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.ipadx = 68;
            gridBagConstraints.ipady = 10;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.insets = new java.awt.Insets(13, 23, 13, 0);
            eqStopPanel.add(jLabel1, gridBagConstraints);

            //scrollPane.setViewportView(eqStopTextField);

            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
            gridBagConstraints.ipadx = 170;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.weightx = 1.0;
            gridBagConstraints.weighty = 1.0;
            gridBagConstraints.insets = new java.awt.Insets(13, 4, 13, 0);
            eqStopPanel.add(scrollPane, gridBagConstraints);

            eqStopTextField.setColumns(15);
            eqStopTextField.setText(equityStop+"");
            eqStopPanel.add(eqStopTextField, new java.awt.GridBagConstraints());

            setEqStopButton.setText("Set");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 2;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.insets = new java.awt.Insets(14, 10, 13, 195);
            eqStopPanel.add(setEqStopButton, gridBagConstraints);

            javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
            this.setLayout(layout);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ButtonPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(eqStopPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
            layout.setVerticalGroup(
                    layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                    .addComponent(ButtonPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(eqStopPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)));
            return this;
        }// </editor-fold>

        private void setUpColumns() {
            for (int i = 0; i < table.getColumnCount(); i++) {
                if (table.getColumnClass(i).isEnum()) {
                    JComboBox comboBox = new JComboBox();
                    for (Object object : table.getColumnClass(i).getEnumConstants()) {
                        comboBox.addItem(object);
                    }
                    table.getColumnModel().getColumn(i).setCellEditor(new DefaultCellEditor(comboBox));
                    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
                    renderer.setToolTipText("Click to choose an " + table.getColumnClass(i).getName());
                    table.getColumnModel().getColumn(i).setCellRenderer(renderer);
                    continue;
                } else if (table.getColumnClass(i) == Period.class) {
                    JComboBox comboBox = new JComboBox();
                    for (Period period : Period.values()) {
                        comboBox.addItem(period);
                    }
                    table.getColumnModel().getColumn(i).setCellEditor(new DefaultCellEditor(comboBox));
                    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
                    renderer.setToolTipText("Click to choose a Period");
                    table.getColumnModel().getColumn(i).setCellRenderer(renderer);
                    continue;
                }
            }//end of outer for
        }//end of method setUpColumns
        // Variables declaration - do not modify
        private javax.swing.JPanel ButtonPanel;
        private javax.swing.JButton addButton;
        private javax.swing.JButton setEqStopButton;
        private javax.swing.JLabel jLabel1;
        private javax.swing.JPanel eqStopPanel;
        private javax.swing.JTextField eqStopTextField;
        private javax.swing.JButton removeButton;
        private javax.swing.JButton startAllButton;
        private javax.swing.JButton startButton;
        private javax.swing.JButton stopAllButton;
        private javax.swing.JButton stopButton;
        // End of variables declaration
    }

    /*
     * Class which creates table data. 
     */
    private class MyTableModel extends AbstractTableModel {

        private Object[] columnNames = getColNames();
        private Object[] columnLabels = getColLabels();

        public Object[] getColNames() {
            List<String> names = new ArrayList<String>();
            Field[] fields = strategiesList.get(0).getClass().getFields();
            for (Field field : fields) {
                Configurable parameter;
                if (field.getName().equals("started")) {
                    names.add("started");
                }
                parameter = field.getAnnotation(Configurable.class);
                if (parameter != null) {
                    names.add(field.getName());
                }
            }
            return names.toArray();
        }

        public Object[] getColLabels() {
            List<String> labels = new ArrayList<String>();
            Field[] fields = strategiesList.get(0).getClass().getFields();
            for (Field field : fields) {
                if (field.getName().equals("started")) {
                    try {
                        String label = "Started";
                        labels.add(label);
                    } catch (Exception ex) {
                        log(ex);
                    }
                }
                Configurable parameter = field.getAnnotation(Configurable.class);
                if (parameter != null) {
                    String label = parameter.value();
                    if (label == null || "".equals(label)) {
                        label = field.getName();
                    }
                    labels.add(label);
                }
            }
            return labels.toArray();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public int getRowCount() {
            return strategiesList.size();
        }

        @Override
        public String getColumnName(int col) {
            return columnLabels[col].toString();
        }

        @Override
        public Object getValueAt(int row, int col) {
            //return data[row][col];
            try {
                return strategiesList.get(row).getClass().getField(columnNames[col].toString()).get(strategiesList.get(row));
            } catch (Exception e) {
                log(e);
            }
            return null;
        }
        /*
         * This method is required to show e.g. check-boxes instead of simple 'true/false' text
         * if the value is boolean. 
         */

        @Override
        public Class getColumnClass(int c) {
            try {
                return strategiesList.get(0).getClass().getField(columnNames[c].toString()).get(strategiesList.get(0)).getClass();
            } catch (Exception e) {
                log(e);
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            String colName = table.getColumnName(col);
            if (colName.equals("Log")) {
                return true;
            }
            if (colName.equals("Started")) {
                return false;
            }
            if ((Boolean) table.getValueAt(row, 5)) {
                return false;
            }
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {

            try {
                strategiesList.get(row).getClass().getField(columnNames[col].toString()).set(strategiesList.get(row), value);
            } catch (Exception e) {
                log(e);
            }
            fireTableCellUpdated(row, col);
        }
    }//end of class MyTableModel

    /**
     * returns correct path in strategies/files folder and if it not exists,
     * create it
     *
     * @param folder
     * @return
     */
    private String getPath(String folder) {
        File direcory = new File(context.getFilesDir() + "\\" + folder);
        if (!direcory.exists()) {
            direcory.mkdir();
        }
        return direcory.getAbsolutePath();
    }
    /*
     * saves the configurations of strategies in a xml file by using 
     * jaxp-dom technique. 
     */

    private boolean saveConfig() {
        try {
            //Create Document
            DocumentBuilderFactory docBuildFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuildFactory.newDocumentBuilder();
            Document document = docBuilder.newDocument();
            //create elements and add to document
            Element strategiesNode = document.createElement("Strategies");
            document.appendChild(strategiesNode);

            for (IStrategy strategy : strategiesList) {
                //create bridge element and add
                Element strategyNode = document.createElement("Strategy");
                strategiesNode.appendChild(strategyNode);

                for (Field field : strategy.getClass().getFields()) {
                    if (field.getAnnotation(Configurable.class) != null) {
                        Element element = document.createElement(field.getName());
                        Text textValue;
                        if (field.getType().isEnum()) {
                            textValue = document.createTextNode("" + ((Enum<?>) field.get(strategy)).ordinal());
                        } else {
                            textValue = document.createTextNode(field.get(strategy).toString());
                            if (field.getType() == Period.class) {
                                textValue = document.createTextNode(((Period) field.get(strategy)).name());
                            }
                        }
                        element.appendChild(textValue);
                        strategyNode.appendChild(element);
                    }
                }
                Text textValue;
                JForexFstBridge bridge = (JForexFstBridge) strategy;
                Element element = document.createElement("started");
                textValue = document.createTextNode(bridge.started + "");
                element.appendChild(textValue);
                strategyNode.appendChild(element);
            }

            //write to file:
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            PrintWriter printWriter = new PrintWriter(brigdesFile);
            StreamResult result = new StreamResult(printWriter);
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
            printWriter.close();

            //creating multiBridge options XML
            Document optionsDoc = docBuilder.newDocument();
            Element optionsNode = optionsDoc.createElement("Options");
            optionsDoc.appendChild(optionsNode);
            Element optionNode = optionsDoc.createElement("Option");
            optionsNode.appendChild(optionNode);
            Text textValue;
            Element element = optionsDoc.createElement("equityStop");
            textValue = optionsDoc.createTextNode(equityStop + "");
            element.appendChild(textValue);
            optionNode.appendChild(element);

            //write to file:
            printWriter = new PrintWriter(optionsFile);
            result = new StreamResult(printWriter);
            source = new DOMSource(optionsDoc);
            transformer.transform(source, result);
            printWriter.close();




        } catch (Exception exception) {
            log(exception);
        }

        return true;
    }
}
