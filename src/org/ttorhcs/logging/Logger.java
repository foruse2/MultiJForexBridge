/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ttorhcs.logging;

import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Period;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 *
 * logger object writes log in the console and the log file
 */
public class Logger {

    public boolean debug = false, info = false;
    private LogLevel logLevel;
    private IConsole console;
    private BufferedWriter out = null;
    private String connId;

    public Logger(IContext context, LogLevel logLevel, String logFileDir, String identity) {
        this.logLevel = logLevel;
        this.connId = identity;
        try {
            if (logLevel.getCode() != 0) {
                console = context.getConsole();
                FileWriter fstream;
                String path = getPath(logFileDir);
                new LogMaintain(path, Period.DAILY);
                String logFile = getPath(path) + "\\" + identity + ".log";
                fstream = new FileWriter(logFile, false);
                out = new BufferedWriter(fstream);
            }
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    public synchronized void debug(String message) {
        if (logLevel.getCode() > 2) {
            console.getOut().println(connId + " D:" + message);
            try {
                out.append("D: " + message + "\n");
                out.flush();
            } catch (Exception ex) {
                console.getOut().println("File access error: " + ex.getMessage());
            }
        }
    }

    public synchronized void debug(Exception e) {
        if (logLevel.getCode() > 2) {
            e.printStackTrace(console.getOut());
            try {
                out.append("D: " + e + "\n");
                out.flush();
            } catch (Exception ex) {
                console.getOut().println("File access error: " + ex.getMessage());
            }
        }
    }

    public synchronized void info(String message) {
        if (logLevel.getCode() > 1) {
            console.getOut().println(connId + " I:" + message);
            try {
                out.append("I: " + message + "\n");
                out.flush();
            } catch (Exception ex) {
                console.getOut().println("File access error: " + ex.getMessage());
            }
        }
    }

    public synchronized void error(Exception e) {
        if (logLevel.getCode() > 0) {
            console.getOut().println(connId + " E: error occured:");
            e.printStackTrace(console.getOut());
            //console.getOut().println(connId+" E:"+e.getMessage());
            try {
                out.append("E: " + e.getMessage() + "\n");
                out.flush();
            } catch (Exception ex) {
                console.getOut().println("File access error: " + ex.getMessage());
            }
        }
    }

    public synchronized void error(String message) {
        if (logLevel.getCode() > 0) {
            console.getOut().println(connId + " E:" + message);
            try {
                out.append("E: " + message + "\n");
                out.flush();
            } catch (Exception ex) {
                console.getOut().println("File access error: " + ex.getMessage());
            }
        }
    }

    public void close() {
        try {
            if (null != out) {
                out.flush();
                out.close();
            }
        } catch (IOException ex) {
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
