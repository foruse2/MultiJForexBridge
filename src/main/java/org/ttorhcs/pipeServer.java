package org.ttorhcs;

import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import org.ttorhcs.logging.Logger;

/*
 * screates pipe server using Kernel32 class
 */
public class pipeServer {

    private String username = System.getProperty("user.name");
    private String prefix = "\\\\.\\pipe\\FST-MT4_", data;
    protected HANDLE pipe;
    protected ByteBuffer buf = null;
    protected IntByReference bytesRead = new IntByReference();
    protected boolean connected = false;
    private static final int TIMEOUT = 0;
    private static final int outBufferSize = 50 * 1024;
    private static final int inBufferSize = 2 * 1024;
    private Logger log;
    private int connId = 0;
    Charset charset = Charset.forName("UTF-8");

    public pipeServer(int connId, Logger log) {
        try {
            this.log = log;
            openPipe(prefix + username + "-" + connId, Kernel32.PIPE_ACCESS_DUPLEX);
            this.connId = connId;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void openPipe(String fileName, int openMode) throws IOException {
        pipe = Kernel32.INSTANCE.CreateNamedPipe(fileName,
                openMode,
                Kernel32.PIPE_TYPE_BYTE | Kernel32.PIPE_READMODE_BYTE | Kernel32.PIPE_WAIT,
                Kernel32.PIPE_UNLIMITED_INSTANCES, outBufferSize, inBufferSize, TIMEOUT, null);
        if (WinBase.INVALID_HANDLE_VALUE.equals(pipe)) {
            pipe = null;
        }
    }

    public String read() throws IOException {

        buf = null;
        buf = ByteBuffer.allocate(inBufferSize);
        boolean success = Kernel32.INSTANCE.ReadFile(pipe, buf, inBufferSize, bytesRead, 0);
        if (success) {
            return bufferToString(buf);
        }
        return "";
    }

    public boolean write(String msg) throws IOException {
        if (null == msg) {
            msg = " ";
        }
        buf = null;
        buf = stringToBuffer(msg);
        boolean success = Kernel32.INSTANCE.WriteFile(pipe, buf, msg.length(), bytesRead, 0);
        return success;
    }

    public void connect() {
        connected = Kernel32.INSTANCE.ConnectNamedPipe(pipe, null);
    }

    public void disconnect() {
        try {
            RandomAccessFile stopPipe = new RandomAccessFile("\\\\.\\pipe\\FST-MT4_" + System.getProperty("user.name") + "-" + connId, "rw");
            stopPipe.writeUTF("STOP");
            Thread.sleep(10);
        } catch (Exception e) {
        }
        if (connected) {
            Kernel32.INSTANCE.DisconnectNamedPipe(pipe);
        }
    }

    public void closePipe() {
        try{
        if (pipe == null) {
            return;
        }
        try {
            RandomAccessFile stopPipe = new RandomAccessFile("\\\\.\\pipe\\FST-MT4_" + System.getProperty("user.name") + "-" + connId, "rw");
            stopPipe.writeUTF("STOP");
            Thread.sleep(10);
        } catch (Exception e) {
        }
        if (null != pipe && connected) {
            Kernel32.INSTANCE.DisconnectNamedPipe(pipe);
            connected = false;
            Kernel32.INSTANCE.CloseHandle(pipe);
        }
        if (null != pipe) {
            Kernel32.INSTANCE.DisconnectNamedPipe(pipe);
            Kernel32.INSTANCE.CloseHandle(pipe);
        }
        pipe = null;
        }catch(Exception e){
            System.out.println(e);
        }
    }

    private String bufferToString(ByteBuffer buffer) {

        CharsetDecoder decoder = charset.newDecoder();
        data = "";
        try {
            int old_position = buffer.position();
            data = decoder.decode(buffer).toString();
            // reset buffer's position to its original so it is not altered:
            buffer.position(old_position);
        } catch (Exception e) {
            log.error(e);
            return "";
        }
        return data;
    }

    private ByteBuffer stringToBuffer(String msg) {
        CharsetEncoder encoder = charset.newEncoder();
        buf = ByteBuffer.allocate(msg.length());
        try {
            buf = encoder.encode(CharBuffer.wrap(msg));
        } catch (CharacterCodingException e) {
            log.error(e);
        }
        return buf;
    }
}
