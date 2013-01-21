/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ttorhcs;

import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;

/*
 * gives bridge and creates new thread for fast tick execution
 *
 */
public class BridgeThread extends Thread {
    public JForexFstBridge bridge;
    public Instrument instrument;
    private ITick tick;
    public boolean tickSended = false;
    public boolean tickDone = true;
    public boolean stop = false;

    public BridgeThread(JForexFstBridge bridge, Instrument instrument) {
        this.instrument = instrument;
        this.bridge = bridge;
    }

    public synchronized void sendTick(ITick tick) {
        tickSended = true;
        this.tick = tick;
        this.notify();
    }

    @Override
    public synchronized void run() {
        try {
            while (!stop) {
                this.wait();
                if (bridge.started) {
                    bridge.onTick(instrument, tick);
                }
                tickDone = true;
            }
        } catch (InterruptedException ex) {
        } catch (JFException ex) {
        }
    }
    
}
