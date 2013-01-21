/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ttorhcs.logging;

/**
 *
 * @author Rendszergazda
 */
public enum LogLevel {
    
    /**
     *
     */
    NONE(0), ERROR(1), INFO(2), DEBUG(3);
    
    private int level;
    
   private LogLevel(int c) {
   level = c;
 }
 
 public int getCode() {
   return level;
 }
    
}
