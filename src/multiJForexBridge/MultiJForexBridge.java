package multiJForexBridge;

import com.dukascopy.api.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.ttorhcs.*;
import org.ttorhcs.logging.LogLevel;
import org.ttorhcs.logging.Logger;
import org.ttorhcs.logging.XlsEquityLogger;
import org.w3c.dom.*;

@RequiresFullAccess
@Library("jForexBridge.jar;jna-3.3.0.jar;jna-3.3.0-platform.jar;poi-ooxml-3.9.jar;poi-3.9.jar")
public class MultiJForexBridge implements IStrategy {

    public List<BridgeThread> strategiesList = new ArrayList<BridgeThread>();
    //define variables for GUI
    public JTable table;
    public JFrame frame;
    public JScrollPane scrollPane;
    public Table tableModel;
    boolean onStartStarted = false;
    //strategy variables
    public IContext context;
    public IConsole console;
    public IUserInterface userInterface;
    boolean modifyAllowed = true;
    /*
     * At the main bridge's startup there will be properties indicating 
     * several options.
     */
    public File brigdesFile, optionsFile;
    public int equityStop = 0;
    public boolean equityLog = true;
    public int maxLeverage = 30;
    private boolean eqStopActivated;
    public Logger log;
    private XlsEquityLogger equityLogger = null;

    @Override
    public void onStart(final IContext context) throws JFException {

        this.context = context;
        this.console = context.getConsole();
        this.userInterface = context.getUserInterface();

        List<Boolean> startedList = new ArrayList<Boolean>();
        brigdesFile = new File(getPath("config") + "\\bridges.xml");
        optionsFile = new File(getPath("config") + "\\options.xml");
        log = new Logger(context, LogLevel.ERROR, context.getFilesDir() + "\\logs", "MultiBridge");
        if (brigdesFile != null && !"".equals(brigdesFile.getName()) && brigdesFile.isFile()
                && (brigdesFile.getName().endsWith(".xml") || brigdesFile.getName().endsWith(".XML"))
                && (brigdesFile.length() != 0)) {
            //read strategies and add to the list
            startedList = readFromXML();
        } else {
            strategiesList.add(new BridgeThread(new JForexFstBridge(), Instrument.EURUSD));
            startedList.add(false);
        }
        saveConfig();
        createEQLogger();

        tableModel = new Table(strategiesList, console);
        table = new JTable(tableModel);
        table.setPreferredScrollableViewportSize(new Dimension(600, 70));
        table.setFillsViewportHeight(true);
        scrollPane = new JScrollPane(table);

        try {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    MainGui myPanel = new MainGui(multiJForexBridge.MultiJForexBridge.this, equityStop, maxLeverage, equityLog, scrollPane);
                    Container content = myPanel.createAndShowGUI();

                    frame = new JFrame("MultiJForexBridge");
                    frame.setContentPane(content);
                    frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    frame.pack();
                    //get the dimensions of the screen
                    Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
                    //frame.setSize(850, 400);
                    // Determine the new location of the window                            
                    int x = (dim.width) / 2 - frame.getWidth() / 2;
                    int y = (dim.height) / 2 - frame.getHeight() / 2;
                    frame.setVisible(true);
                    frame.setLocation(x, y);

                }
            });
        } catch (Exception exception) {
            log.error(exception);
        }

        /*
         * start bridges if flagged as started
         */
        for (int i = 0; i < strategiesList.size(); i++) {
            if (startedList.get(i)) {
                JForexFstBridge bridge = strategiesList.get(i).bridge;
                try {
                    bridge.onStart(context);
                    if (!strategiesList.get(i).isAlive()) {
                        strategiesList.get(i).start();
                    }
                } catch (Exception exception) {
                    log.error(exception);
                    bridge.started = false;
                }
            }
            onStartStarted = true;
        }



    }//end of the onStart method

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
                        if (variableName.equals("eqLog")) {
                            equityLog = Boolean.parseBoolean(nodeValue);
                        }
                        if (variableName.equals("maxLeverage")) {
                            int parsedLeverage = Integer.parseInt(nodeValue);
                            if (parsedLeverage > 0) {
                                maxLeverage = Integer.parseInt(nodeValue);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("invalid options file: " + optionsFile.getName());
            log.error(e);
            equityStop = 0;
        }

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
                //setting maximum leverage
                newBridge.maxLeverage = maxLeverage;
                strategiesList.add(new BridgeThread(newBridge, Instrument.EURUSD));
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
            log.error("invalid config file: " + brigdesFile.getName());
            log.error(e);
            strategiesList.clear();
            strategiesList.add(new BridgeThread(new JForexFstBridge(), Instrument.EURUSD));
            startedList.add(false);
        }

        if (strategiesList.isEmpty()) {
            strategiesList.add(new BridgeThread(new JForexFstBridge(), Instrument.EURUSD));
            startedList.add(false);
        }

        return startedList;
    }//end of method readFromXML

    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        modifyAllowed = false;
        try {
            if (!onStartStarted) {
                return;
            }
            for (BridgeThread st : strategiesList) {
                st.tickSended = false;
            }
            for (BridgeThread st : strategiesList) {
                if (!st.tickSended && instrument == st.instrument && st.bridge.started) {
                    st.sendTick(tick);
                }
            }
            if (!eqStopActivated) {
                activateEquityStop();
            }
//waiting all bridges to done
            boolean alldone = false;
            int tryes = 0;
            while (!alldone) {
                if (!alldone) {
                    Thread.sleep(20);
                }
                alldone = true;
                for (BridgeThread st : strategiesList) {
                    if (!st.tickDone && st.bridge.started) {
                        alldone = false;
                    }
                }
                tryes++;
                if (tryes > 9) {
                    alldone = true;
                }
            }
            Thread.sleep(3);
        } catch (InterruptedException ex) {
        }
        modifyAllowed = true;

    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!onStartStarted) {
            return;
        }
        if (period == Period.ONE_HOUR && equityLog) {
            createEQLogger();
            equityLogger.logEQBAL(askBar.getTime() + Period.ONE_HOUR.getInterval(), context.getAccount().getEquity(), context.getAccount().getBalance());
        }
        for (BridgeThread st : strategiesList) {
            JForexFstBridge bridge = st.bridge;
            if (bridge.period == period && bridge.started) {
                bridge.onBar(instrument, period, askBar, bidBar);
            }
        }
        if (period == Period.ONE_HOUR) {
            saveEqBalance();
        }

    }

    @Override
    public void onMessage(IMessage message) throws JFException {
        if (!onStartStarted) {
            return;
        }
        for (BridgeThread st : strategiesList) {
            JForexFstBridge bridge = st.bridge;
            if (bridge.started) {
                bridge.onMessage(message);
            }
        }
        if (message.getType() == IMessage.Type.ORDER_CLOSE_OK) {
            IOrder closedOrder = message.getOrder();
            for (BridgeThread st : strategiesList) {
                try{
                if ((Integer.parseInt(closedOrder.getLabel().replace("S", "").split("_")[0]) == st.bridge.magic)) {
                    equityLogger.logTrade(closedOrder);
                }
                }catch(Exception e){
                    
                }
            }
        }
    }

    @Override
    public void onAccount(IAccount account) throws JFException {
        if (!onStartStarted) {
            return;
        }
        for (BridgeThread st : strategiesList) {
            JForexFstBridge bridge = st.bridge;
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
        Collection<BridgeThread> collectionToRemove = new ArrayList<BridgeThread>();
        for (BridgeThread st : strategiesList) {
            collectionToRemove.add(st);
        }
        removeFromList(collectionToRemove);
        //userInterface.removeBottomTab(TAB_KEY);
        if (frame != null) {
            frame.setVisible(false);
        }
        closeEqLogger();
        log.close();
    }

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

    public boolean saveConfig() {
        try {
            //Create Document
            DocumentBuilderFactory docBuildFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuildFactory.newDocumentBuilder();
            Document document = docBuilder.newDocument();
            //create elements and add to document
            Element strategiesNode = document.createElement("Strategies");
            document.appendChild(strategiesNode);

            //creating multiBridge options XML
            Document optionsDoc = docBuilder.newDocument();
            Element optionsNode = optionsDoc.createElement("Options");
            optionsDoc.appendChild(optionsNode);
            Element optionNode = optionsDoc.createElement("Option");
            optionsNode.appendChild(optionNode);
            // equityStop value
            Element eEqStop = optionsDoc.createElement("equityStop");
            Text eqStopValue = optionsDoc.createTextNode(equityStop + "");
            eEqStop.appendChild(eqStopValue);
            optionNode.appendChild(eEqStop);
            //maxLeverage variable
            Element eMaxLev = optionsDoc.createElement("maxLeverage");
            Text mlValue = optionsDoc.createTextNode(maxLeverage + "");
            eMaxLev.appendChild(mlValue);
            optionNode.appendChild(eMaxLev);
            //eqLog
            Element eEqLog = optionsDoc.createElement("eqLog");
            Text eqLogValue = optionsDoc.createTextNode(equityLog + "");
            eEqLog.appendChild(eqLogValue);
            optionNode.appendChild(eEqLog);

            //write to file:
            if (!optionsFile.exists()) {
                optionsFile = new File(getPath("config") + "\\options.xml");
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            PrintWriter printWriter = new PrintWriter(optionsFile);
            StreamResult result = new StreamResult(printWriter);
            DOMSource source = new DOMSource(optionsDoc);
            transformer.transform(source, result);
            printWriter.close();

            for (BridgeThread st : strategiesList) {
                JForexFstBridge strategy = st.bridge;
                //create bridge eStarted and add
                Element strategyNode = document.createElement("Strategy");
                strategiesNode.appendChild(strategyNode);

                for (Field field : strategy.getClass().getFields()) {
                    if (field.getAnnotation(Configurable.class) != null) {
                        Element e = document.createElement(field.getName());
                        Text tv;
                        if (field.getType().isEnum()) {
                            tv = document.createTextNode("" + ((Enum<?>) field.get(strategy)).ordinal());
                        } else {
                            tv = document.createTextNode(field.get(strategy).toString());
                            if (field.getType() == Period.class) {
                                tv = document.createTextNode(((Period) field.get(strategy)).name());
                            }
                        }
                        e.appendChild(tv);
                        strategyNode.appendChild(e);
                    }
                }
                JForexFstBridge bridge = (JForexFstBridge) strategy;

                //started variable
                Element eStarted = document.createElement("started");
                Text startValue = document.createTextNode(bridge.started + "");
                eStarted.appendChild(startValue);
                strategyNode.appendChild(eStarted);
            }

            //write to file:
            if (!brigdesFile.exists()) {
                brigdesFile = new File(getPath("config") + "\\bridges.xml");
            }
            printWriter = new PrintWriter(brigdesFile);
            result = new StreamResult(printWriter);
            source = new DOMSource(document);
            transformer.transform(source, result);
            printWriter.close();

        } catch (Exception exception) {
            log.error(exception);
        }

        return true;
    }

    public void removeFromList(Collection<BridgeThread> collectionToRemove) {

        while (!modifyAllowed) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException ex) {
            }
        }
        for (BridgeThread st : collectionToRemove) {
            try {
                if (st.bridge.started) {
                    st.bridge.onStop();
                }
            } catch (JFException ex) {
            }
            st.stop = true;
            st.sendTick(null);
        }
        strategiesList.removeAll(collectionToRemove);
    }

    public void addToList(JForexFstBridge bridge) {

        while (!modifyAllowed) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
            }
        }
        BridgeThread st = new BridgeThread(bridge, Instrument.EURUSD);
        st.start();
        strategiesList.add(st);
    }

    public void saveEqBalance() {
        if (equityLog) {
        }
        //TODO create saveEqBalance
    }

    private void activateEquityStop() throws JFException {
        if (context.getAccount().getEquity() < equityStop) {
            for (BridgeThread st : strategiesList) {
                JForexFstBridge bridge = st.bridge;
                if (bridge.started) {
                    bridge.onStop();
                }
            }
            tableModel.fireTableChanged(new TableModelEvent(tableModel));
            for (IOrder order : context.getEngine().getOrders()) {
                order.close();
            }
            log.error("Stopped all bridge because equity goes below Equity Stop");
            eqStopActivated = true;
            saveConfig();
        }
    }

    public void closeEqLogger() {
        if (null != equityLogger) {
            equityLogger.close();
            equityLogger = null;
        }
    }

    public void createEQLogger() {
        if (null == equityLogger && equityLog) {
            equityLogger = equityLogger = new XlsEquityLogger(context, context.getFilesDir() + "\\equityLogs", log, equityLog);
        }
    }
}
