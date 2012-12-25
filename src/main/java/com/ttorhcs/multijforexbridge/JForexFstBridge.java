package com.ttorhcs.multijforexbridge;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IDataService;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.RequiresFullAccess;
import com.dukascopy.api.system.TesterFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@RequiresFullAccess
public class JForexFstBridge implements IStrategy {

    public IHistory history;
    public IEngine engine;
    public IAccount account;
    public IConsole console;
    public IContext context;
    public IDataService dataService;
    public int maxTicket = 1000000000, breakEven = 0, trailingStop = 0;
    public server serverThis;
    public boolean runServer = true;
    public RandomAccessFile clientPipe = null;
    public String lastTickStr;
    @Configurable("Magic")
    public int magic = 10002000;
    public @Configurable("Period:")
    Period period = Period.ONE_HOUR;
    public @Configurable("connection ID:")
    int connId = 111;
    public @Configurable("Log")
    boolean log = false;
    public @Configurable("Instrument:")
    Instrument instrument = Instrument.EURUSD;
    private IOrder position;
    private IOrder newOrder = null;
    private String fileName;
    private boolean firstTick = false;
    public boolean started = false;

    @Override
    public void onStart(IContext context) throws JFException {
        this.history = context.getHistory();
        this.engine = context.getEngine();
        this.account = context.getAccount();
        this.dataService = context.getDataService();
        this.console = context.getConsole();
        this.context = context;
        
        //subscribe to instrument
        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(instrument);
        context.setSubscribedInstruments(instruments, true);
        
        serverThis = new server();
        serverThis.instrument = instrument;
        serverThis.dataService = dataService;
        serverThis.history = history;
        serverThis.account = account;
        serverThis.context = context;
        position = getLastPosition();
        serverThis.start();
        fileName = getPath("logs")+"\\"+(System.currentTimeMillis() / 1000) +"_"+ connId + ".log";
        started = true;

        log("Started");
    }

    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (!dataService.isOfflineTime(tick.getTime()) && instrument == this.instrument) {
            tickToFST(tick, instrument);
            if (breakEven > 0) {
                checkBreakEven(breakEven, tick);
            }
            if (trailingStop > 0) {
                setTrailingStops(trailingStop, tick);
            }
        }
    }

    /**
     * writes tick message to FST pipe
     *
     * @param tick
     * @param instrument
     */
    public void tickToFST(ITick tick, Instrument instrument) {
        String rtnString = "TI " + createTick(tick, instrument);

        try {
            if (null == clientPipe) {
                createClientPipe();
            }
            if (null != clientPipe) {
                log(rtnString);
                clientPipe.write(rtnString.getBytes());
                byte[] lpInBuffer = new byte[2];
                clientPipe.read(lpInBuffer, 0, 2);
                String respond = new String(lpInBuffer, "UTF-8");
                log(respond);
                clientPipe.close();
                clientPipe = null;
                if ("OK".equals(respond.toUpperCase())) {
                } else {
                    log("error in Tick send: \n" + rtnString + "\n respond: " + respond);
                }
            }
        } catch (IOException e) {
            log("cannot communicate with FST pipe");
            clientPipe = null;

        }
    }

    /**
     * creates return tick message
     *
     * @param tick
     * @param instrument
     * @return
     */
    public String createTick(ITick tick, Instrument instrument) { // sends a
        // tick
        // to FST
        String rtnString = "";
        DecimalFormat df = new DecimalFormat("#.00000");
        try {
            rtnString = instrument.name() + " " + period.getInterval() / 60000 + " " + (tick.getTime() / 1000) + " " + tick.getBid() + " " + tick.getAsk() + " "
                    + (Math.round((tick.getAsk() - tick.getBid()) * 100000)) + " " + df.format(instrument.getPipValue() * 10000).replace(',', '.') + " "
                    + barToStrForTick(history.getBar(instrument, period, OfferSide.ASK, 0)) + " "
                    + history.getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, 12, history.getStartTimeOfCurrentBar(instrument, period), 0).get(1).getTime() / 1000
                    + " " + account.getBalance() + " " + account.getEquity() + " " + (float) Math.round((account.getEquity() - account.getBalance()) * 100) / 100 + " "
                    + (float) Math.round((account.getEquity() - (account.getEquity() * account.getUseOfLeverage())) * 100) / 100 // accountfreemargin
                    + " " + getPositionDetails();
        } catch (JFException e) {
            rtnString = "ER";
            log(e);
        }
        lastTickStr = rtnString;
        return rtnString;
    }

    /**
     * creates client pipe from connId and loggen in user if pipe not exists
     * then wait for it
     *
     * @return
     */
    public boolean createClientPipe() {
        clientPipe = null;
        int tryes = 1;
        while (tryes < 5 && null == clientPipe) {
            try {
                clientPipe = new RandomAccessFile("\\\\.\\pipe\\MT4-FST_" + System.getProperty("user.name") + "-" + connId, "rw");
            } catch (FileNotFoundException e) {
                // log(e);
                log("waiting for Fst pipe..");
                try {
                    clientPipe = null;
                    Thread.sleep(50);
                    tryes++;
                } catch (InterruptedException ex) {
                    return false;
                }
                if (tryes > 20) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onMessage(IMessage message) throws JFException {

        System.out.println(message.getContent());
        if (message.getType() == IMessage.Type.ORDER_FILL_OK) {
            System.out.println("order filled: " + message.getContent());
            position = getLastPosition();
            newOrder = position;

        }
        if (message.getType() == IMessage.Type.ORDER_CLOSE_OK) {
            IOrder closedOrder = message.getOrder();
            log("closed order: " + closedOrder.getLabel());
            if (message.getReasons().contains(IMessage.Reason.ORDER_CLOSED_BY_SL)) {
                serverThis.consecutiveLosses++;
                serverThis.closedSLTPLots = closedOrder.getAmount() * 10;
                serverThis.activatedSL = closedOrder.getClosePrice();
            } else if (message.getReasons().contains(IMessage.Reason.ORDER_CLOSED_BY_TP)) {
                serverThis.activatedTP++;
                serverThis.consecutiveLosses = 0;
                serverThis.closedSLTPLots = closedOrder.getAmount() * 10;
                serverThis.activatedTP = closedOrder.getClosePrice();
            } else {
                if (closedOrder.getProfitLossInUSD() > 0) {
                    serverThis.consecutiveLosses = 0;
                } else {
                    serverThis.consecutiveLosses++;
                }
            }

        }

    }

    @Override
    public void onAccount(IAccount account) throws JFException {
    }

    @Override
    public void onStop() throws JFException {
        runServer = false;
        try {
            if (clientPipe != null){
                clientPipe.writeUTF("STOPPED");
            }
            Thread.sleep(500);
            if (serverThis.isAlive()) {
                try {
                    RandomAccessFile stopPipe = new RandomAccessFile("\\\\.\\pipe\\FST-MT4_" + System.getProperty("user.name") + "-" + connId, "rw");
                    stopPipe.writeUTF("ST");
                    byte[] lpInBuffer = new byte[7];
                    stopPipe.read(lpInBuffer, 0, 7);
                    String respond = new String(lpInBuffer, "UTF-8");
                    log("OK! server: "+respond);

                } catch (Exception ex) {
                    log(ex);
                }
            }
        } catch (Exception ex) {
            log(ex);
        }
        started = false;
    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (instrument == this.instrument && period == this.period) {
            firstTick = true;
        }
    }

    /**
     * sets trailing stops if given
     *
     * @param trailingStop
     * @param tick
     */
    public void setTrailingStops(int trailingStop, ITick tick) {
        try {
            double stoplossPrice;
            for (IOrder order : getPositions()) {
                if (order.isLong()) {
                    if (order.getStopLossPrice() == 0) {
                        stoplossPrice = order.getOpenPrice() - (order.getInstrument().getPipValue() * (trailingStop / 10));
                        order.setStopLossPrice(stoplossPrice, OfferSide.BID);
                    } else {
                        stoplossPrice = order.getStopLossPrice();
                    }
                    if ((tick.getBid() - stoplossPrice) > (order.getInstrument().getPipValue() * (trailingStop / 10))) {
                        double price = tick.getBid() - (order.getInstrument().getPipValue() * (trailingStop / 10));
                        order.setStopLossPrice(price, OfferSide.BID);
                    }
                } else {
                    if (order.getStopLossPrice() == 0) {
                        stoplossPrice = order.getOpenPrice() + (order.getInstrument().getPipValue() * (trailingStop / 10));
                        order.setStopLossPrice(stoplossPrice, OfferSide.ASK);
                    } else {
                        stoplossPrice = order.getStopLossPrice();
                    }
                    if ((stoplossPrice - tick.getAsk()) > (order.getInstrument().getPipValue() * (trailingStop / 10))) {
                        double price = tick.getAsk() + (order.getInstrument().getPipValue() * (trailingStop / 10));
                        order.setStopLossPrice(price, OfferSide.ASK);
                    }
                }
            }
        } catch (JFException ex) {
            log(ex);
        }
    }

    /**
     * if break even is set this method checks price and set if it nesessary
     *
     * @param breakeven
     * @param tick
     */
    public void checkBreakEven(int breakeven, ITick tick) {
        try {
            double breakevenprice;
            int newBreakeven = 0;
            for (IOrder order : getPositions()) {
                if (order.isLong()) {
                    if (order.getOpenPrice() > order.getStopLossPrice()) {
                        breakevenprice = order.getOpenPrice() - (order.getInstrument().getPipValue() * ((breakeven / 10) + 2));
                        if ((tick.getBid() - breakevenprice) > (order.getInstrument().getPipValue() * (breakeven / 10))) {
                            double price = tick.getBid() - (order.getInstrument().getPipValue() * ((breakeven / 10) + 2));
                            order.setStopLossPrice(price, OfferSide.BID);
                        }
                    } else {
                        newBreakeven = breakEven;
                    }
                } else {
                    if (order.getStopLossPrice() > order.getOpenPrice()) {
                        breakevenprice = order.getOpenPrice() + (order.getInstrument().getPipValue() * ((breakeven / 10) - 2));
                        if ((breakevenprice - tick.getAsk()) > (order.getInstrument().getPipValue() * (breakeven / 10))) {
                            double price = tick.getAsk() + (order.getInstrument().getPipValue() * ((breakeven / 10) - 2));
                            order.setStopLossPrice(price, OfferSide.ASK);
                        } else {
                            newBreakeven = breakEven;
                        }
                    }
                }
            }
            breakEven = newBreakeven;
        } catch (JFException ex) {
            log(ex);
        }
    }
/**
 * returns correct path in strategies/files folder and if it not exists, create it 
 * @param folder
 * @return 
 */
    private String getPath(String folder) {
        File direcory = new File(context.getFilesDir()+"\\"+folder);
        if(!direcory.exists()){
            direcory.mkdir();
        }
        return direcory.getAbsolutePath();
    }

    public class server extends Thread {

        public int consecutiveLosses = 0;
        public double activatedSL = 0, activatedTP = 0, closedSLTPLots = 0;
        private String msg;
        private java.util.List<IBar> bars;
        private boolean run = true;
        public Instrument instrument;
        public IHistory history;
        public IDataService dataService;
        public IAccount account;
        public IContext context;

        @Override
        public void run() {
            String reply = "";
            fstBars(1, 20);
            pipeServer server = new pipeServer(connId);
            server.connect();
            while (run) {
                try {
                    server.connect();
                    msg = server.read().trim();
                    log("read: " + msg.trim());
                    reply = processMessage(msg);
                    log("write: " + reply.substring(0, (reply.length() > 200 ? 200 : reply.length())));
                    server.write(reply);
                    server.disconnect();
                    Thread.sleep(10);
                    this.run = runServer;
                } catch (Exception e) {
                    server.disconnect();
                    log(e);
                }
            }
        }

        /**
         * process command and call related method
         *
         * @param msg2
         * @return
         * @throws JFException
         */
        private String processMessage(String msg2) throws JFException {
            String cmd = extractOrder(msg2);

            if (cmd.equals("ST")) {
                return ("STOPPED");
            }
            if (cmd.equals("PI")) {
                // System.out.println("ping respond");
                return fstPing();
            } else if (cmd.equals("MA")) {
                // System.out.println("Market Info All Respond");
                return fstMarketInfoAll();
            } else if (cmd.equals("AI")) {
                return fstAccountInfo();
            } else if (cmd.equals("TE")) {
                return fstTerminalInfo();
            } else if (cmd.equals("BR")) {
                // System.out.println(msg.split("\\s+")[4]);
                int barCount = Integer.parseInt(msg.split("\\s+")[4].trim());
                int offset = Integer.parseInt(msg.split("\\s+")[3].trim());
                return fstBars(offset, barCount);
            } else if (cmd.equals("OS")) {
                return fstOrderSend();
            } else if (cmd.equals("OC")) {
                String[] msgArray = msg.split(" ");
                double lotsToClose = Double.parseDouble(msgArray[2]);
                return fstOrderClose(lotsToClose);
            } else if (cmd.equals("OM")) {
                return fstOrderModify();
            }
            return "ER";
        }

        /**
         * extract command from FST command
         *
         * @param msg2
         * @return
         */
        private String extractOrder(String msg2) {
            String cmd = "";
            cmd = msg2.substring(0, 2).toUpperCase();

            return cmd;
        }

        /**
         * creates ping iformation if time is non trading time it returns last
         * tick information
         *
         * @return
         */
        private String fstPing() {
            try {
                String response = "";
                ITick tick = history.getLastTick(instrument);
                if (!dataService.isOfflineTime(tick.getTime())) {
                    DecimalFormat df = new DecimalFormat("#.00000");
                    response += "OK "
                            + instrument.name()
                            + " "
                            + // symbol
                            (period.getInterval() / 1000 / 60)
                            + " "
                            + // period
                            (tick.getTime() / 1000)
                            + " "
                            + // time
                            tick.getBid()
                            + " "
                            + // bid
                            tick.getAsk()
                            + " "
                            + // ask
                            Math.round(((tick.getAsk() - tick.getBid()) * 100000))
                            + " "
                            + // spread
                            df.format(instrument.getPipValue() * 10000).replace(',', '.')
                            + " "
                            + // tickValue
                            barToStr(history.getBar(instrument, period, OfferSide.ASK, 0))
                            + " "
                            + // openTime, open, high, low, close, volume
                            (history.getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, 12, history.getStartTimeOfCurrentBar(instrument, period), 0).get(1).getTime() / 1000)
                            + " " + // bartime10
                            account.getBalance() + " " + // accountbalance
                            account.getEquity() + " " + // accountequity
                            (float) Math.round((account.getEquity() - account.getBalance()) * 100) / 100 + " " + // accountprofit
                            (float) Math.round((account.getEquity() - (account.getEquity() * account.getUseOfLeverage())) * 100) / 100 + " " + // accountfreemargin
                            getPositionDetails(); // position details
                } else {
                    response = "OK " + lastTickStr;
                }
                return response;
            } catch (JFException e) {
                log(e);
                return "ERR cannot get Ping details";
            }
        }

        /**
         * gets market information
         *
         * @return
         */
        private String fstMarketInfoAll() {
            String response = "ER";
            try {
                DecimalFormat df = new DecimalFormat("0.00000");
                ITick tick = history.getLastTick(instrument);
                response = "OK " + df.format(instrument.getPipValue() / 10) + " " + // point
                        (instrument.getPipValue() > 0.0001 ? "3.00000" : "5.00000 ") + // digits
                        Math.round(((tick.getAsk() - tick.getBid()) * 100000)) + " " + // spread
                        df.format(account.getStopLossLevel()).replace(',', '.') + " " + // stopLevel
                        "100000.00000 " + // lotSize
                        df.format(instrument.getPipValue() * 10000).replace(',', '.') + " " + // tickValue
                        "0.00001 " + // tickSize
                        df.format(getLongOvernight(instrument)).replace(',', '.') + " " + // swapLong
                        df.format(getShortOvernight(instrument)).replace(',', '.') + " " + // swapShort
                        "0.00000 " + // starting
                        "0.00000 " + // expiration
                        // df.format((account.getAccountState()
                        // ==
                        // IAccount.AccountState.OK ) ?
                        // 1:0).replace(',',
                        // '.')+" "+
                        // //tradeAllowed
                        df.format(dataService.isOfflineTime(System.currentTimeMillis() / 1000) ? 1 : 0).replace(',', '.') + " " + // tradeAllowed
                        "0.01000 " + // minLot
                        "0.01000 " + // lotStep
                        "99999.00000 " + // maxLot
                        "0.00000 " + // swapType
                        "0.00000 " + // profitCalcMode
                        "0.00000 " + // marginCalcMode
                        "0.00000 " + // marginInit
                        "0.00000 " + // marginMaintenance
                        "50000.00000 " + // marginHedged
                        df.format(tick.getAsk() * 1000).replace(',', '.') + " " + // marginRequired
                        "0.00000"; // freezeLevel
            } catch (Exception e) {
                log(e);
                return "ER";
            }
            return response;
        }

        /**
         * gets jForex account information
         *
         * @return
         */
        private String fstAccountInfo() {
            String response = "";
            response += "OK NOT_APLLICABLE " + (account.getAccountId().length() < 1 ? 132456789 : account.getAccountId()) + " " + "Dukascopy_Bank " + "jForex-terminal "
                    + account.getCurrency().getCurrencyCode() + " " + (int) account.getLeverage() + " " + account.getBalance() + " " + account.getEquity() + " "
                    + (account.getEquity() - account.getBalance()) + " " + "0.00 " + (account.getBalance() * account.getUseOfLeverage()) + " " + "1 "
                    + (account.getBalance() - (account.getBalance() * account.getUseOfLeverage())) + " " + "0 " + (account.getMarginCutLevel() / 10) + " "
                    + ((engine.getType() == IEngine.Type.LIVE) ? 0 : 1);
            return response;
        }

        private String fstTerminalInfo() {
            // OK MetaTrader_-_Alpari_UK Alpari_(UK)_Ltd.
            // C:|Program_Files|MetaTrader_-_Alpari_UK 1.10 1.4
            String respond = "OK jForex_-_Dukascopy_Bank_SA " + "not_relevant " + "1.10 1.4";

            return respond;
        }

        /**
         *
         * @param offsetFrom
         * @param offsetTo
         * @return bars in String format from a given offset
         */
        private String fstBars(int offsetFrom, int offsetTo) {
            String result = "OK " + instrument.name() + " " + (int) (period.getInterval() / 1000 / 60) + " 2000 " + offsetFrom + " ";
            try {
                IBar barTo = history.getBar(instrument, period, OfferSide.ASK, 0);
                if (offsetFrom == 1) {
                    bars = history.getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, offsetTo + 10, barTo.getTime(), 0);
                }
                if (offsetTo + 10 > bars.size()) {
                    bars = history.getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, offsetTo + 10, barTo.getTime(), 0);
                }
                if (bars.get(bars.size() - 1).getVolume() == 0) {
                    bars.remove(bars.size() - 1);
                }
                IBar ibar;
                int totalBars = (bars.size() - 1) - offsetFrom;
                int returnBars = 0;
                String barsString = "";
                for (int i = totalBars; i >= ((bars.size() - 1) - (offsetTo)); i--) {
                    ibar = bars.get(i);
                    if (barsString.length() > 50000) {
                        break;
                    }
                    barsString += " " + barToStr(ibar);
                    returnBars++;
                }
                result += (returnBars) + "" + barsString;
            } catch (Exception e) {
                log(e);
            }
            return result;
        }

        /**
         *
         * @return next unique label for new orders
         */
        private String getNextLabel() {
            return "S" + magic + "_" + (maxTicket);
        }

        /**
         * sends an order to jForex from FST
         *
         * @return
         */
        private String fstOrderSend() {
            try {
                double stoplossprice = 0, takeprofitPrice = 0;
                OrderCommand direction;
                // OS EURUSD 1 1 1.27206 21 0 0 0 0 TS1=0;BRE=0
                String[] order = msg.split(" ");
                parseOrderParameters(order[10]);
                double amount = (Double.parseDouble(order[3])) / 10;
                double price = Double.parseDouble(order[4]);
                int stoploss = Integer.parseInt(order[6]);
                if (stoploss == 0 && trailingStop > 0) {
                    stoploss = trailingStop;
                }
                int takeprofit = Integer.parseInt(order[7]);
                String label = getNextLabel();
                String comment = "cl=" + consecutiveLosses + ";aSL=" + activatedSL + ";aTP=" + activatedTP + ";al=" + closedSLTPLots;
                if (Integer.parseInt(order[2]) == 0) {
                    direction = OrderCommand.BUY;
                    if (price == 0) {
                        price = history.getLastTick(instrument).getAsk();
                    }
                    if (stoploss > 0) {
                        stoplossprice = price - ((stoploss / 10) * instrument.getPipValue());
                    }
                    if (takeprofit > 0) {
                        takeprofitPrice = price + ((takeprofit / 10) * instrument.getPipValue());
                    }
                } else {
                    direction = OrderCommand.SELL;
                    if (price == 0) {
                        price = history.getLastTick(instrument).getBid();
                    }
                    if (stoploss > 0) {
                        stoplossprice = price + ((stoploss / 10) * instrument.getPipValue());
                    }
                    if (takeprofit > 0) {
                        takeprofitPrice = price - ((takeprofit / 10) * instrument.getPipValue());
                    }
                }
                setTrailingStop(Integer.parseInt(order[10].split(";")[0].split("=")[1]));
                setBreakEven(Integer.parseInt(order[10].split(";")[1].split("=")[1]));
                if (null != position) { // there is a position
                    if (direction.isLong() == position.isLong()) {
                        OrderTask task = new OrderTask(label, instrument, direction, amount, stoplossprice, takeprofitPrice, comment);
                        context.executeTask(task);
                    } else {
                        return fstOrderClose(amount * 10);
                    }
                } else {
                    OrderTask task = new OrderTask(label, instrument, direction, amount, stoplossprice, takeprofitPrice, comment);
                    context.executeTask(task);
                }
                int waitFor = 1;
                while (newOrder == null && waitFor < 100) {
                    try {
                        if (newOrder == null) {
                            for (IOrder o : engine.getOrders()) {
                                if (o.getLabel().equals(label)) {
                                    o = newOrder;
                                    break;
                                }
                            }
                            wait(50);
                            waitFor++;
                        }
                    } catch (Exception e) {
                        // newOrder = position;
                    }
                }
                if (newOrder != null) {
                    if (newOrder.getState() == com.dukascopy.api.IOrder.State.FILLED) {
                        newOrder = null;
                        return "OK " + maxTicket;
                    }
                }
            } catch (Exception e) {
                // console.getOut().print(e.getMessage());
                log(e);
            }
            return "ER";
        }

        private void setBreakEven(int brEvPips) {
            if (brEvPips >= 100) {
                breakEven = brEvPips;
            } else {
                breakEven = 0;
            }
        }

        private void setTrailingStop(int trStPips) {
            if (trStPips >= 100) {
                trailingStop = trStPips;
            } else {
                trailingStop = 0;
            }
        }

        /**
         * closes orders opened by this strategies lasts as long as the amount of
         * @param lotsToClose desired closing amount
         * @return
         */
        private String fstOrderClose(double lotsToClose) {
            IOrder order = null;
            log("close: " + lotsToClose);
            try {
                order = getFirstPosition();
                int tryes = 0;
                while (lotsToClose > (double) 0.001 && null != order && tryes < 20) {
                    if ((order.getAmount() * 10) <= lotsToClose) {
                        log("trying to close: " + order.getLabel());
                        lotsToClose -= order.getAmount() * 10;
                        closeTask closetask = new closeTask(order, order.getAmount());
                        context.executeTask(closetask);
                        log("closed: " + tryes + " remaining lots: " + lotsToClose);
                    } else {
                        log("trying to close: " + order.getLabel());
                        closeTask closetask = new closeTask(order, lotsToClose / 10);
                        context.executeTask(closetask);

                        lotsToClose = 0;
                        log("closed: " + tryes);
                        break;
                    }
                    Thread.sleep(10);
                    tryes++;
                    order = getNextPosition(order);
                }
            } catch (Exception e) {
                log(e);
                return "ER";
            }
            return "OK 0";
        }
        
        private void parseOrderParameters(String parameters) {
            int brEven = Integer.parseInt(parameters.split(";")[1].substring(4));
            int trStop = Integer.parseInt(parameters.split(";")[0].substring(4));
            setTrailingStop(trStop);

            if (brEven < trStop) {
                setBreakEven(brEven);
            } else {
                setBreakEven(0);
            }
        }

        /**
         * Modify given order SL ad TP levels
         *
         * @return order
         */
        private String fstOrderModify() {
            String[] msgArray = msg.split(" ");
            parseOrderParameters(msgArray[6]);
            double price = Double.parseDouble(msgArray[2]);
            int stoploss = Integer.parseInt(msgArray[3]);
            if (stoploss == 0 && trailingStop > 0) {
                stoploss = trailingStop;
            }
            int takeprofit = Integer.parseInt(msgArray[4]);
            double stoplossprice = 0, takeprofitPrice = 0;
            OfferSide offSide;
            IOrder order = getFirstPosition();

            if (order.isLong()) {
                if (stoploss > 0) {
                    stoplossprice = price - ((double) (stoploss / 10) * instrument.getPipValue());
                }
                if (takeprofit > 0) {
                    takeprofitPrice = price + ((double) (takeprofit / 10) * instrument.getPipValue());
                }
                offSide = OfferSide.ASK;
            } else {
                if (stoploss > 0) {
                    stoplossprice = price + ((double) (stoploss / 10) * instrument.getPipValue());
                }
                if (takeprofit > 0) {
                    takeprofitPrice = price - ((double) (takeprofit / 10) * instrument.getPipValue());
                }
                offSide = OfferSide.BID;
            }
            for (IOrder iOrder : getPositions()) {
                if (order.isLong() != iOrder.isLong()) {
                    closeTask closetask = new closeTask(iOrder, iOrder.getAmount());
                    context.executeTask(closetask);
                    log("positions in different directions!: " + magic);
                    continue;
                }
                if (iOrder.getStopLossPrice() != stoplossprice) {
                    modifyStoploss mSL = new modifyStoploss(iOrder, stoplossprice, offSide);
                    context.executeTask(mSL);
                }
                if (iOrder.getTakeProfitPrice() != takeprofitPrice) {
                    modifyTakeProfit mTP = new modifyTakeProfit(iOrder, takeprofitPrice);
                    context.executeTask(mTP);
                }
            }
            return "OK";
        }

        /**
         *
         * @param instrument
         * @return specifyed instrument long swap
         */
        private double getLongOvernight(Instrument instrument) {
            try {
                Map<Instrument, Double> map = TesterFactory.getDefaultInstance().getOvernights().getLongOvernights();
                for (Instrument key : map.keySet()) {
                    if (key == instrument) {
                        return map.get(key);
                    }
                }
            } catch (ClassNotFoundException e) {
                log(e);
            } catch (IllegalAccessException e) {
                log(e);
            } catch (InstantiationException e) {
                log(e);
            }
            return 0;
        }

        /**
         *
         * @param instrument
         * @return specifyed instrument short swap
         */
        private double getShortOvernight(Instrument instrument) {
            try {
                Map<Instrument, Double> map = TesterFactory.getDefaultInstance().getOvernights().getShortOvernights();
                for (Instrument key : map.keySet()) {
                    if (key == instrument) {
                        return map.get(key);
                    }
                }
            } catch (Exception e) {
                log(e);
            }
            return 0;
        }

        /**
         * return proper format of martingale details to FST
         *
         * @return
         */
        public synchronized String getMartingaleDetails() {
            DecimalFormat df = new DecimalFormat("0.00000");
            return " cl=" + consecutiveLosses + ";aSL=" + df.format(activatedSL).replace(',', '.') + ";aTP=" + df.format(activatedTP).replace(',', '.') + ";al="
                    + df.format(closedSLTPLots).replace(',', '.');
        }

        public synchronized void setMartingaleDetails(int consLosses, double actSl, double actTp, double clSLTPLots) {
            consecutiveLosses = consLosses;
            activatedSL = actSl;
            activatedTP = actTp;
            closedSLTPLots = clSLTPLots;
        }

        /*
         * private void print(Object o){ log(o); }
         */
    }

    /**
     * sends an order to jForex terminal from strategy outer thread
     */
    public class OrderTask implements Callable<IOrder> {

        private Instrument instrument;
        private double amount, stoplossprice, takeProfitPrice;
        private OrderCommand command;
        private String label, comment;

        public OrderTask(String label, Instrument instrument, OrderCommand command, double amount, double stoplossprice, double takeProfitPrice, String comment) {
            this.instrument = instrument;
            this.stoplossprice = stoplossprice;
            this.takeProfitPrice = takeProfitPrice;
            this.amount = amount;
            this.command = command;
            this.label = label;
            this.comment = comment;

        }

        @Override
        public IOrder call() {
            try {
                System.out.println(label + " " + instrument + " " + command + " " + amount + " " + stoplossprice + " " + takeProfitPrice + " " + comment);
                return engine.submitOrder(this.label, this.instrument, this.command, this.amount, 0, 5, this.stoplossprice, this.takeProfitPrice, 0, this.comment);
                // return engine.submitOrder("test123", Instrument.EURUSD ,
                // OrderCommand.BUY, 0.1 , 0, 5, 0,0);
            } catch (Exception e) {
                log(e);
                return null;
            }
        }
    }

    /**
     * closes an order in jForex terminal from strategy outer thread
     */
    public class closeTask implements Callable<IOrder> {

        IOrder order;
        double amount;

        public closeTask(IOrder order, double amount) {
            this.order = order;
            this.amount = amount;
        }

        @Override
        public IOrder call() throws Exception {
            order.close(amount);
            return order;
        }
    }

    /**
     * modify an order stoploss to jForex terminal from strategy outer thread
     */
    public class modifyStoploss implements Callable<IOrder> {

        IOrder order;
        double stopLossPrice;
        OfferSide offSide;

        public modifyStoploss(IOrder order, double stopLossPrice, OfferSide offSide) {
            this.order = order;
            this.stopLossPrice = stopLossPrice;
            this.offSide = offSide;
        }

        @Override
        public IOrder call() throws Exception {
            order.setStopLossPrice(stopLossPrice, offSide);
            return order;
        }
    }

    /**
     * modify an order take profit to jForex terminal from strategy outer thread
     */
    public class modifyTakeProfit implements Callable<IOrder> {

        IOrder order;
        double takeprofitPrice;

        public modifyTakeProfit(IOrder order, double takeprofitPrice) {
            this.order = order;
            this.takeprofitPrice = takeprofitPrice;
        }

        @Override
        public IOrder call() throws Exception {
            order.setTakeProfitPrice(takeprofitPrice);
            return order;
        }
    }

    /**
     *
     * @param bar
     * @return bar data in string format
     */
    public String barToStr(IBar bar) {
        String rs = "";
        rs += (int) (bar.getTime() / 1000) + " ";
        rs = rs + bar.getOpen() + " ";
        rs = rs + bar.getHigh() + " ";
        rs = rs + bar.getLow() + " ";
        rs += bar.getClose() + " ";
        rs += Math.round(bar.getVolume() * 10) / 10;
        return rs;
    }

    /**
     *
     * @param bar
     * @return bar data in string format and if it is a first tick in period/bar
     * the volume = 0
     */
    public String barToStrForTick(IBar bar) {
        String rs = "";
        rs += (int) (bar.getTime() / 1000) + " ";
        rs = rs + bar.getOpen() + " ";
        rs = rs + bar.getHigh() + " ";
        rs = rs + bar.getLow() + " ";
        rs += bar.getClose() + " ";
        if (firstTick) {
            rs += "1";
            firstTick = false;
        } else {
            rs += Math.round(bar.getVolume() * 10) / 10;
        }
        return rs;
    }

    /**
     * searches all filled orders opened by this strategy
     */
    private void getHistory() {
        IOrder order = null;
        long lastFillTime = 0;
        int consecutiveLosses = 0;
        double activatedSL = 0, activatedTP = 0, closedSLTPLots = 0;
        try {
            for (IOrder o : engine.getOrders()) {
                if (checkLabel(o) && o.getFillTime() > lastFillTime) {
                    order = o;
                }
                if (order != null) {
                    maxTicket = getOrderTicket(order);
                    consecutiveLosses = Integer.parseInt(order.getComment().split(";")[0].substring(3));
                    activatedSL = Double.parseDouble(order.getComment().split(";")[1].substring(4));
                    activatedTP = Double.parseDouble(order.getComment().split(";")[2].substring(4));
                    closedSLTPLots = Double.parseDouble(order.getComment().split(";")[3].substring(3));
                    serverThis.setMartingaleDetails(consecutiveLosses, activatedSL, activatedTP, closedSLTPLots);
                }
            }
        } catch (JFException e) {
            log(e);
        }
    }

    /**
     *
     * @return return this strategy live orders from Dukas server.
     */
    private List<IOrder> getPositions() {
        List<IOrder> orderList = new ArrayList<IOrder>();
        try {
            for (IOrder order : engine.getOrders()) {
                if (checkLabel(order) && order.getState() == IOrder.State.FILLED) {
                    orderList.add(order);
                }
            }
        } catch (JFException e) {
            log(e);
        }
        return orderList;
    }

    /**
     *
     * @return returns first opened position by this strategy
     */
    private IOrder getFirstPosition() {
        IOrder firstOrder = null;
        int minTicket = maxTicket;
        try {
            for (IOrder o : getPositions()) {
                if (minTicket >= getOrderTicket(o)) {
                    firstOrder = o;
                    minTicket = getOrderTicket(o);
                }
            }
        } catch (Exception e) {
        }
        return firstOrder;
    }

    /**
     *
     * @param o - IOrder
     * @return emulated orderTicket
     */
    private int getOrderTicket(IOrder o) {
        // log("getOrderTicket: "+Integer.parseInt(o.getLabel().split("_")[1])
        // );
        return Integer.parseInt(o.getLabel().split("_")[1]);
    }

    /**
     *
     * @return last opened position by this strategy
     */
    private IOrder getLastPosition() {
        IOrder rtnOrder = null;
        for (IOrder order : getPositions()) {
            if (getOrderTicket(order) >= maxTicket) {
                rtnOrder = order;
            }
        }
        if (null != rtnOrder) {
            maxTicket = getOrderTicket(rtnOrder);
        } else {
            getHistory();
            position = null;
        }
        return rtnOrder;
    }

    /**
     *
     * @param order - IOrder
     * @return true if checked order opened with this strategy (compares MAGIC)
     */
    private boolean checkLabel(IOrder order) {
        return order.getLabel().replace("S", "").split("_")[0].equals(magic + "");
    }

    /**
     *
     * @return all opened position profit
     */
    private double getPositionProfit() {
        double posProfit = 0;
        for (IOrder order : getPositions()) {
            posProfit += order.getProfitLossInAccountCurrency();
        }
        return posProfit;
    }

    /**
     *
     * @return position details
     */
    private String getPositionDetails() {
        String rtnString = "";
        position = getLastPosition();

        if (null != position) {
            rtnString += "" + maxTicket; // apositionTicket
            rtnString += " " + (position.isLong() ? 0 : 1); // string
            // apositionType
            // 0BUY 1SELL
            rtnString += " " + (getPositionAmount() * 10);
            rtnString += " " + position.getOpenPrice();
            rtnString += " " + (int) (position.getFillTime() / 1000);
            rtnString += " " + position.getStopLossPrice();
            rtnString += " " + position.getTakeProfitPrice();
            rtnString += " " + ((double) Math.round(getPositionProfit() * 100) / 100);
            rtnString += " ID=" + connId + ",_MAGIC=" + magic + serverThis.getMartingaleDetails();
        } else {
            rtnString = "0 -1 0.00 0.00000 1577836800 0.00000 0.00000 0.00 " + serverThis.getMartingaleDetails();
        }
        return rtnString;
    }

    /**
     *
     * @return summary position amount
     */
    private double getPositionAmount() {
        double posAmount = 0;
        for (IOrder order : getPositions()) {
            posAmount += order.getAmount();
        }
        return posAmount;
    }

    /**
     *
     * @param order
     * @return next position in opened ordering
     */
    private IOrder getNextPosition(IOrder order) {
        int ticket = getOrderTicket(order);
        List<IOrder> orderList = getPositions();
        for (int i = 0; i < orderList.size(); i++) {
            ticket++;
            for (IOrder o : orderList) {
                if (getOrderTicket(o) == ticket) {
                    return o;
                }
            }
        }
        return null;
    }

    private void log(String message) {
        if (log) {
            console.getOut().println(message);
            try {
                FileWriter fstream = new FileWriter(fileName, true);
                BufferedWriter out = new BufferedWriter(fstream);
                out.append(message + "\n");
                out.close();
            } catch (Exception ex) {
                console.getOut().print("File access error: " + ex.getMessage());
            }
        }
    }

    private void log(Exception e) {
        if (log) {
            e.printStackTrace(console.getErr());
            try {
                FileWriter fstream = new FileWriter(fileName, true);
                BufferedWriter out = new BufferedWriter(fstream);
                out.append(e + "\n");
                out.close();
            } catch (Exception ex) {
                console.getOut().print("File access error: " + ex.getMessage());
            }
        }
    }
}
