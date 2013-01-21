/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ttorhcs.logging;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @
 * maintaints log folders: if file older than 24h and folder contains more than
 * 10 file, then deletes it
 *
 */
public class LogMaintain {

    public LogMaintain(String folder) {

        maintainFolder(folder);
    }

    private void maintainFolder(String folder) {
        if (null == folder || folder.equals("")) {
            return;
        }
        File f = new File(folder);
        if (f.isDirectory()) {
            List<File> logFiles = new ArrayList<File>();
            logFiles.addAll(Arrays.asList(f.listFiles()));
            
            //sort logfile to lastModified order
            if (!logFiles.isEmpty()) {
                Collections.sort(logFiles, new Comparator<File>() {
                    @Override
                    public int compare(File f1, File f2) {
                        return Long.signum(f1.lastModified() - f2.lastModified());
                    }
                });
            }
            // delete first 10 logfile
            int i = 0;
            for (File slf : logFiles) {

                if ((i + 10) < logFiles.size() && (slf.lastModified() < System.currentTimeMillis() - 86400000)) {
                    try {
                        slf.delete();
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }
                i++;
            }
        }

    }
}
