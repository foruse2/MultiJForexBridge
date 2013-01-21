package org.ttorhcs;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import java.nio.Buffer;

/*
 * native connection to win32 API
 * 
 */
public interface Kernel32 extends WinNT {

    Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class,
            W32APIOptions.UNICODE_OPTIONS);
    public static final int PIPE_ACCESS_INBOUND = 0x00000001;
    public static final int PIPE_ACCESS_OUTBOUND = 0x00000002;
    public static final int PIPE_ACCESS_DUPLEX = 0x00000003;
    //
    // Define the Named Pipe End flags for GetNamedPipeInfo
    //
    public static final int PIPE_CLIENT_END = 0x00000000;
    public static final int PIPE_SERVER_END = 0x00000001;
    //
    // Define the dwPipeMode values for CreateNamedPipe
    //
    public static final int PIPE_WAIT = 0x00000000;
    public static final int PIPE_NOWAIT = 0x00000001;
    public static final int PIPE_READMODE_BYTE = 0x00000000;
    public static final int PIPE_READMODE_MESSAGE = 0x00000002;
    public static final int PIPE_TYPE_BYTE = 0x00000000;
    public static final int PIPE_TYPE_MESSAGE = 0x00000004;
    public static final int PIPE_ACCEPT_REMOTE_CLIENTS = 0x00000000;
    public static final int PIPE_REJECT_REMOTE_CLIENTS = 0x00000008;
    public static final int NMPWAIT_WAIT_FOREVER = 0xffffffff;
    public static final int NMPWAIT_NOWAIT = 0x00000001;
    public static final int NMPWAIT_USE_DEFAULT_WAIT = 0x00000000;
    public static final int GENERIC_READ = 0x00000008;
    public static final int GENERIC_WRITE = 0x00000004;
    public static final int GENERIC_EXECUTE = 0x00000002;
    public static final int GENERIC_ALL = 0x00000001;
    public static final int OPEN_EXISTING = 0x00000003;
    //
    // Define the well known values for CreateNamedPipe nMaxInstances
    //
    public static final int PIPE_UNLIMITED_INSTANCES = 255;

    HANDLE CreateNamedPipe(
            String lpName,
            int dwOpenMode,
            int dwPipeMode,
            int nMaxInstances,
            int nOutBufferSize,
            int nInBufferSize,
            int nDefaultTimeOut,
            WinBase.SECURITY_ATTRIBUTES lpSecurityAttributes);

//    HANDLE WINAPI CreateFile(
//    		  __in      LPCTSTR lpFileName,
//    		  __in      DWORD dwDesiredAccess,
//    		  __in      DWORD dwShareMode,
//    		  __in_opt  LPSECURITY_ATTRIBUTES lpSecurityAttributes,
//    		  __in      DWORD dwCreationDisposition,
//    		  __in      DWORD dwFlagsAndAttributes,
//    		  __in_opt  HANDLE hTemplateFile
//    		);
    HANDLE CreateFile(
            String lpName,
            int dwDesiredAccess,
            int dwShareMode,
            WinBase.SECURITY_ATTRIBUTES lpSecurityAttributes,
            int dwCreationDisposition,
            int dwFlagsAndAttributes,
            HANDLE hTemplateFile);

//	    BOOL WINAPI CallNamedPipe(
//	    		  __in   LPCTSTR lpNamedPipeName,
//	    		  __in   LPVOID lpInBuffer,
//	    		  __in   DWORD nInBufferSize,
//	    		  __out  LPVOID lpOutBuffer,
//	    		  __in   DWORD nOutBufferSize,
//	    		  __out  LPDWORD lpBytesRead,
//	    		  __in   DWORD nTimeOut
//	    		);
    boolean CallNamedPipe(
            String lpNamedPipeName,
            Byte[] lpInBuffer,
            int nInBufferSize,
            Byte[] lpOutBuffer,
            int nOutBufferSize,
            Byte[] lpBytesRead,
            int nTimeOut);

//    WINBASEAPI
//    BOOL
//    WINAPI
//    ConnectNamedPipe(
//        __in        HANDLE hNamedPipe,
//        __inout_opt LPOVERLAPPED lpOverlapped
//        );
    boolean ConnectNamedPipe(HANDLE hNamedPipe, WinBase.OVERLAPPED lpOverlapped);

//    WINBASEAPI
//    BOOL
//    WINAPI
//    DisconnectNamedPipe(
//        __in HANDLE hNamedPipe
//        );
    boolean DisconnectNamedPipe(HANDLE hNamedPipe);

    boolean CloseHandle(HANDLE hObject);

    /**
     * Reads data from the specified file or input/output (I/O) device. Reads
     * occur at the position specified by the file pointer if supported by the
     * device.
     *
     * This function is designed for both synchronous and asynchronous
     * operations. For a similar function designed solely for asynchronous
     * operation, see ReadFileEx
     *
     * @param hFile A handle to the device (for example, a file, file stream,
     * physical disk, volume, console buffer, tape drive, socket, communications
     * resource, mailslot, or pipe).
     * @param lpBuffer A pointer to the buffer that receives the data read from
     * a file or device.
     * @param nNumberOfBytesToRead The maximum number of bytes to be read.
     * @param lpNumberOfBytesRead A pointer to the variable that receives the
     * number of bytes read when using a synchronous hFile parameter
     * @param lpOverlapped A pointer to an OVERLAPPED structure is required if
     * the hFile parameter was opened with FILE_FLAG_OVERLAPPED, otherwise it
     * can be NULL.
     * @return If the function succeeds, the return value is nonzero (TRUE). If
     * the function fails, or is completing asynchronously, the return value is
     * zero (FALSE). To get extended error information, call the GetLastError
     * function.
     *
     * Note The GetLastError code ERROR_IO_PENDING is not a failure; it
     * designates the read operation is pending completion asynchronously. For
     * more information, see Remarks.
     */
    boolean ReadFile(
            HANDLE hFile,
            Buffer lpBuffer,
            int nNumberOfBytesToRead,
            IntByReference lpNumberOfBytesRead,
            int lpOverlapped);

    boolean WriteFile(
            HANDLE hFile,
            Buffer lpBuffer,
            int nNumberOfBytesToWrite,
            IntByReference lpNumberOfBytesWritten,
            int lpOverlapped);

    boolean SetNamedPipeHandleState(
            HANDLE hNamedPipe,
            IntByReference lpMode,
            IntByReference lpMaxCollectionCount,
            WinBase.OVERLAPPED lpCollectDataTimeout);

    /**
     * The FormatMessage function formats a message string. The function
     * requires a message definition as input. The message definition can come
     * from a buffer passed into the function. It can come from a message table
     * resource in an already-loaded module. Or the caller can ask the function
     * to search the system's message table resource(s) for the message
     * definition. The function finds the message definition in a message table
     * resource based on a message identifier and a language identifier. The
     * function copies the formatted message text to an output buffer,
     * processing any embedded insert sequences if requested.
     *
     * @param dwFlags Formatting options, and how to interpret the lpSource
     * parameter. The low-order byte of dwFlags specifies how the function
     * handles line breaks in the output buffer. The low-order byte can also
     * specify the maximum width of a formatted output line.
     * <p/>
     * This version of the function assumes FORMAT_MESSAGE_ALLOCATE_BUFFER is
     * <em>not</em> set.
     * @param lpSource Location of the message definition.
     * @param dwMessageId Message identifier for the requested message.
     * @param dwLanguageId Language identifier for the requested message.
     * @param lpBuffer Pointer to a buffer that receives the null-terminated
     * string that specifies the formatted message.
     * @param nSize This this parameter specifies the size of the output buffer,
     * in TCHARs. If FORMAT_MESSAGE_ALLOCATE_BUFFER is
     * @param va_list Pointer to an array of values that are used as insert
     * values in the formatted message.
     * @return If the function succeeds, the return value is the number of
     * TCHARs stored in the output buffer, excluding the terminating null
     * character. If the function fails, the return value is zero. To get
     * extended error information, call GetLastError.
     */
    int FormatMessage(int dwFlags, Pointer lpSource, int dwMessageId,
            int dwLanguageId, char[] lpBuffer,
            int nSize, Pointer va_list);
}
