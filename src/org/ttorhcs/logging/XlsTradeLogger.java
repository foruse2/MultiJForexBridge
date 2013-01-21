/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ttorhcs.logging;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Period;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 *
 * @author Rendszergazda
 */
public class XlsTradeLogger {

    private int magic, row = 3;
    private Logger log;
    private String folder;
    private Workbook XLS;
    private ByteArrayOutputStream byteOut;
    private String fileName;
    private RandomAccessFile fileOut;
    public boolean headerSetted = false;
    public int numberOfTicks = 0;
    public long startTime = 0, endTime = 0;
    private final boolean logAllowed;

    public XlsTradeLogger(int magic, int connId, String folder, Logger log, boolean tradelogAllowed) {

        this.logAllowed = tradelogAllowed;
        this.magic = magic;
        this.log = log;
        this.folder = getPath(folder);
        if (!logAllowed) {
            headerSetted = true;
            return;
        }
        new LogMaintain(this.folder, Period.WEEKLY);
        provideXLS(this.folder);

    }

    public void close() {
        if (!logAllowed) {
            return;
        }
        try {
            Sheet sheet = XLS.getSheetAt(0);
            for (int j = 0; j < 5; j++) {
                sheet.autoSizeColumn(j);
                sheet.setColumnWidth(j, sheet.getColumnWidth(j) + 1024);
            }
            writeToFS();
            fileOut.close();

        } catch (Exception ex) {
            log.error(ex);
        }
    }

    public void logTrade(IOrder o) {
        if (!logAllowed) {
            return;
        }

        try {
            Sheet sheet;
            if (XLS != null) {
                sheet = XLS.getSheetAt(0);
            } else {
                XLS = WorkbookFactory.create(new File(fileName));
                sheet = XLS.getSheetAt(0);
            }
            Cell cellOT, cellCT, cellAmount, cellPL, cellSIDE;
            String side = (o.isLong() ? "LONG" : "SHORT");

            Row r = sheet.getRow(row);
            if (null == r) {
                r = sheet.createRow(row);
            }
            row++;

            long openTime = o.getFillTime();
            long closeTime = o.getCloseTime();
            double amount = o.getAmount() * 10;
            double pl = o.getProfitLossInPips();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cellOT = getOrCreateCell(r, 0);
            cellSIDE = getOrCreateCell(r, 1);
            cellAmount = getOrCreateCell(r, 2);
            cellCT = getOrCreateCell(r, 3);
            cellPL = getOrCreateCell(r, 4);


            //openDate
            CellStyle cs = XLS.createCellStyle();
            DataFormat df = XLS.createDataFormat();
            cs.setDataFormat(df.getFormat("m/d/yy h:mm"));
            cellOT.setCellStyle(cs);
            cal.setTimeInMillis(openTime);
            cellOT.setCellValue(cal);
            //side
            cellSIDE.setCellType(Cell.CELL_TYPE_STRING);
            cellSIDE.setCellValue(side);
            //amount
            cellAmount.setCellValue(amount);
            //closeTime
            cellCT.setCellStyle(cs);
            cal.setTimeInMillis(closeTime);
            cellCT.setCellValue(cal);
            //PL
            cellPL.setCellValue(pl);

            for (int j = 0; j < 4; j++) {
                sheet.autoSizeColumn(j);
                sheet.setColumnWidth(j, sheet.getColumnWidth(j) + 1024);
            }
            writeToFS();
        } catch (Exception ex) {
            log.error(ex);
        }
    }

    private void provideXLS(String folder) {
        fileName = folder + "\\" + magic + "_" + System.currentTimeMillis() + ".xls";
        File blankCopy = null;
        try {
            FileOutputStream out = new FileOutputStream(fileName);
            Workbook wb = new HSSFWorkbook();
            wb.createSheet(magic + "");
            wb.write(out);
            out.close();
            blankCopy = new File(fileName);

            InputStream inp = new FileInputStream(blankCopy);
            XLS = WorkbookFactory.create(inp);
            fileOut = new RandomAccessFile(fileName, "rw");
        } catch (Exception ex) {
            log.error(ex);
        }
    }

    private Cell getOrCreateCell(Row r, int column) {
        Cell cell = r.getCell(column);
        if (null == cell) {
            cell = r.createCell(column);
        }
        return cell;
    }

    private void writeToFS() {
        try {
            byteOut = new ByteArrayOutputStream();
            XLS.write(byteOut);
            fileOut.seek(0);
            fileOut.write(byteOut.toByteArray());
            Thread.sleep(100);
        } catch (Exception ex) {
            log.error(ex);
        }
    }

    public int calculateColWidth(int width) {
        if (width > 254) {
            return 65280; // Maximum allowed column width. 
        }
        if (width > 1) {
            int floor = (int) (Math.floor(((double) width) / 5));
            int factor = (30 * floor);
            int value = 450 + factor + ((width - 1) * 250);
            return value;
        } else {
            return 450; // default to column size 1 if zero, one or negative number is passed. 
        }
    }

    public void writeHeader(String instrumentText) {
        if (!logAllowed) {
            return;
        }
        int ticksTime = (int) ((endTime - startTime) / 1000 / 60);
        String backtestQuality = (numberOfTicks / ticksTime > 5 ? "Tick" : "MinOHLC");
        //setBacktestQuality
        writeCell(0, 0, backtestQuality);
        //setInstrument
        writeCell(0, 1, instrumentText);
        //column names
        writeCell(2, 0, "OpenDate");
        writeCell(2, 1, "Direction");
        writeCell(2, 2, "Amount");
        writeCell(2, 3, "CloseDate");
        writeCell(2, 4, "PL pips");
        headerSetted = true;
    }

    private void writeCell(int rowNum, int column, String text) {
        Sheet sheet;
        try {
            if (XLS != null) {
                sheet = XLS.getSheetAt(0);
            } else {
                XLS = WorkbookFactory.create(new File(fileName));
                sheet = XLS.getSheetAt(0);
            }
            Row r = sheet.getRow(rowNum);
            if (null == r) {
                r = sheet.createRow(rowNum);
            }
            Cell cell = getOrCreateCell(r, column);
            cell.setCellValue(text);
            writeToFS();
        } catch (Exception ex) {
            log.error(ex);
        }
    }

    /**
     * returns correct path in strategies/files folder and if it not exists,
     * create it
     *
     * @param folder
     * @return
     */
    private String getPath(String folder) {
        File direcory = new File(folder);
        if (!direcory.exists()) {
            direcory.mkdir();
        }
        return direcory.getAbsolutePath();
    }
}
