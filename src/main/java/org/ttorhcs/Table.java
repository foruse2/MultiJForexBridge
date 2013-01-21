/*
 * 
 * manages visual to bridges list panel
 */
package org.ttorhcs;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IConsole;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;


/*
 * Class which creates table data.
 */
public final class Table extends AbstractTableModel {

    public String[] columnNames;
    public String[] columnLabels;
    private List<BridgeThread> strategiesList = new ArrayList<BridgeThread>();
    private IConsole console;

    public Table(List<BridgeThread> strategiesList, IConsole console) {
        this.strategiesList = strategiesList;
        this.console = console;
        columnNames = getColNames();
        columnLabels = getColLabels();
    }

    public String[] getColNames() {
        List<String> names = new ArrayList<String>();
        Field[] fields = strategiesList.get(0).bridge.getClass().getFields();
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
        return names.toArray(new String[names.size()]);
    }

    public String[] getColLabels() {
        List<String> labels = new ArrayList<String>();
        Field[] fields = strategiesList.get(0).bridge.getClass().getFields();
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
        return labels.toArray(new String[labels.size()]);
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

    /*
     * This method is required to show e.g. check-boxes instead of simple 'true/false' text
     * if the value is boolean.
     */

    @Override
    public Class getColumnClass(int c) {
        try {
            return strategiesList.get(0).bridge.getClass().getField(columnNames[c].toString()).get(strategiesList.get(0).bridge).getClass();
        } catch (Exception e) {
            log(e);
        }
        return null;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        String colName = getColumnName(col);
        /*if (colName.equals("Logging")) {
         return true;
         }*/
        if (colName.equals("Started")) {
            return false;
        }
        if ((Boolean) getValueAt(row, 7)) {
            return false;
        }
        return true;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        try {
            strategiesList.get(row).bridge.getClass().getField(columnNames[col].toString()).set(strategiesList.get(row).bridge, value);
        } catch (Exception e) {
            log(e);
        }
        fireTableCellUpdated(row, col);
    }

    public void log(String message) {
        console.getOut().println(message);
    }

    public void log(Exception e) {
        e.printStackTrace(console.getErr());
    }

    @Override
    public Object getValueAt(int row, int col) {
        //return data[row][col];
        try {
            return strategiesList.get(row).bridge.getClass().getField(columnNames[col].toString()).get(strategiesList.get(row).bridge);
        } catch (Exception e) {
            log(e);
        }
        return null;
    }
} //end of class MyTableModel
