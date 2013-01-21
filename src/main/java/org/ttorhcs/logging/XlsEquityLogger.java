/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ttorhcs.logging;

import com.dukascopy.api.IContext;
import com.dukascopy.api.IOrder;
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
 * @author valakiquad
 */
public class XlsEquityLogger {

    private String fileName;
    private Workbook XLS;
    private RandomAccessFile fileOut;
    private Logger log;
    private ByteArrayOutputStream byteOut;
    private final boolean logAllowed;
    private int row = 3;
    private Sheet EQSheet;
    private Sheet AllTradeSheet;
    private int sumTradeRow = 3;

    public XlsEquityLogger(IContext context, String fileName, Logger log, boolean logAllowed) {

        this.logAllowed = logAllowed;
        this.log = log;
        if (logAllowed) {
            String folder = getPath(fileName);
            new LogMaintain(folder);
            provideXLS(folder);
            writeHeader();
        }
    }

    public void logEQBAL( long time, double equity, double balance) {
        if (!logAllowed) {
            return;
        }
        try {
            Sheet sheet;
            if (XLS != null) {
                sheet = XLS.getSheet("Equity");
            } else {
                XLS = WorkbookFactory.create(new File(fileName));
                sheet = XLS.getSheet("Equity");
            }
            Cell cellDate, cellEQ, cellBal;

            Row r = sheet.getRow(row);
            if (null == r) {
                r = sheet.createRow(row);
            }
            row++;

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cellDate = getOrCreateCell(r, 0);
            cellEQ = getOrCreateCell(r, 1);
            cellBal = getOrCreateCell(r, 2);


            //openDate
            CellStyle cs = XLS.createCellStyle();
            DataFormat df = XLS.createDataFormat();
            cs.setDataFormat(df.getFormat("m/d/yy h:mm"));
            cellDate.setCellStyle(cs);
            cal.setTimeInMillis(time);
            cellDate.setCellValue(cal);
            //equity
            cellEQ.setCellValue(equity);
            //balance
            cellBal.setCellValue(balance);

            for (int j = 0; j < 3; j++) {
                sheet.autoSizeColumn(j);
                sheet.setColumnWidth(j, sheet.getColumnWidth(j) + 1024);
            }
            writeToFS();
        } catch (Exception ex) {
            log.error(ex);
        }
    }
    
       public void logTrade(IOrder o) {
        if (!logAllowed) {
            return;
        }

        try {
            Cell cellOT, cellCT, cellAmount, cellPL, cellSIDE, cellMagic;
            String side = (o.isLong() ? "LONG" : "SHORT");

            Sheet sheet = AllTradeSheet;
            Row r = sheet.getRow(sumTradeRow);
            if (null == r) {
                r = sheet.createRow(sumTradeRow);
            }
            sumTradeRow++;

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
            cellMagic = getOrCreateCell(r, 5);


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
            cellMagic.setCellValue(o.getLabel().replace("S", "").split("_")[0]);

            for (int j = 0; j < 6; j++) {
                sheet.autoSizeColumn(j);
                sheet.setColumnWidth(j, sheet.getColumnWidth(j) + 1024);
            }
            writeToFS();
        } catch (Exception ex) {
            log.error(ex);
        }
    }

    private void writeHeader() {
        if (!logAllowed) {
            return;
        }
        // eq log column names
        writeCell(EQSheet, 2, 0, "Date");
        writeCell(EQSheet,2, 1, "Equity");
        writeCell(EQSheet,2, 2, "Balance");
        
        writeCell(AllTradeSheet, 2, 0, "OpenDate");
        writeCell(AllTradeSheet,2, 1, "Direction");
        writeCell(AllTradeSheet,2, 2, "Amount");
        writeCell(AllTradeSheet,2, 3, "CloseDate");
        writeCell(AllTradeSheet,2, 4, "PL pips");
        writeCell(AllTradeSheet,2, 5, "Magic");
    }

    private void provideXLS(String folder) {
        fileName = folder + "\\EQBAL_" + System.currentTimeMillis() + ".xls";
        File blankCopy = null;
        try {
            FileOutputStream out = new FileOutputStream(fileName);
            Workbook wb = new HSSFWorkbook();
            wb.write(out);
            out.close();
            blankCopy = new File(fileName);

            InputStream inp = new FileInputStream(blankCopy);
            XLS = WorkbookFactory.create(inp);
            AllTradeSheet = XLS.createSheet("All_Trade");
            EQSheet = XLS.createSheet("Equity");
            fileOut = new RandomAccessFile(fileName, "rw");
        } catch (Exception ex) {
            log.error(ex);
        }
    }

    private void writeCell(Sheet sheet, int rowNum, int column, String text) {
        try {
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

    public void close() {
        if (!logAllowed) {
            return;
        }
        try {
            writeToFS();
            fileOut.close();

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
