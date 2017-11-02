package fwatch.pjc.fm;

/**
 * @author Friedhold Matz, October 2017.
 * 
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Thread.State;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey; 
import java.nio.file.WatchService;
import java.sql.Timestamp;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import oracle.forms.handler.IHandler;
import oracle.forms.properties.ID;
import oracle.forms.ui.CustomEvent;
import oracle.forms.ui.VBean;
import oracle.forms.api.FException; 

/**
 * Description of Windows File Watcher Service PJC for Oracle Forms:
 * ----------------------------------------------------------------------------- 
 * File Watcher Service for Oracle Forms as included PJC on the client side 
 * can work as a Back-End File Service for Oracle Forms. It's interacting 
 * with other program components on Windows desktops or easily waiting 
 * on desktop events marked as "watching event" files in a user defined 
 * temporary directory. 
 * 
 * A File Watcher Service is represented as a "One Way Directory Watcher Service", 
 * e.g.: 
 *   FORMS =>  [dir\forms2] => FORMS2
 *   FORMS <=  [dir\forms]  <= FORMS2
 *   FORMS =>  [dir\others] => OTHERS
 *   FORMS <=  [dir\forms]  <= OTHERS .
 *
 * You can "Send Actions" / "Receive Results" to/from other File Watcher
 * Services written in e.g. C, C#, GO, Python .. desktop programming languages;
 * a simple file format is used at the moment: - for "Send Action" ::
 * ACTION|Para1|Para2|..|ParaN - for "Receive Result" ::
 * ACTION|Result1|Result2|..|ResultN .
 * (Send & Receive mean write & read files here.)
 * 
 * The Watcher Service can be terminated by sending 'EOwatchService.watch' into the
 * Watcher Directory.
 * 
 * Project in October 2017 - PoC - Version : 0.02.01 
 *
 * @author  : Friedhold Matz - Friedhold.Matz@yahoo.com
 * -----------------------------------------------------------------------------
 */

/* --- Forms PJC class --- */
public class FileWatcherPJC extends VBean implements Runnable {

   /* ( EO / BO means: "End of .." / "Begin of .." . ) */
    
   /* -- BO Forms PJC section --- */
    private static final String DEFAULTMESSAGE = "$$$ PID(WinWatcherPJC) not available. $$$";

   /** Set   ::  Forms sends => PJC    (Forms sends)
    *  Get   ::  PJC   sends => Forms  (Forms receives)
    *  Event ::  PJC   sends => Forms  (Forms receives)
    */
    public static final ID SETSTARTSERVER = ID.registerProperty("SetStartServer");
    public static final ID SETSTOPSERVER  = ID.registerProperty("SetStopServer");
    public static final ID SETKILLSERVER  = ID.registerProperty("SetKillServer");

    public static final ID EVENTGETMSG    = ID.registerProperty("EventGetMsg");
    public static final ID GETMSG         = ID.registerProperty("GetMsg");

    public static final ID EVENTACTION    = ID.registerProperty("EventAction");
    public static final ID GETACTIONPARAS = ID.registerProperty("GetActionParas");

    public static final ID EVENTRESULT    = ID.registerProperty("EventResult");
    public static final ID GETRESULTPARAS = ID.registerProperty("GetResultParas");

    // "send subDir action..|result.."
    public static final ID SENDACTION2FORMS  = ID.registerProperty("SendAction2Forms");
    public static final ID SENDRESULT2FORMS  = ID.registerProperty("SendResult2Forms");
    
    // "send subDir action..|result.." 
    public static final ID SENDACTION2OTHERS = ID.registerProperty("SendAction2Others");
    public static final ID SENDRESULT2OTHERS = ID.registerProperty("SendResult2Others");
       
    /* --- PJC self check --- */
    public static final ID SETCHKGETMSG    = ID.registerProperty("SetChkGetMsg");
    public static final ID EVENTCHKGETMSG  = ID.registerProperty("EventChkGetMsg");
    public static final ID GETCHKMSG       = ID.registerProperty("GetChkMsg");

    /* --- BO version & logger --- */
    public static final ID SETLOGGEROFF    = ID.registerProperty("SetLOGOFF");
    
    public static final ID OS              = ID.registerProperty("GetOS");
    public static final ID OSVERSION       = ID.registerProperty("GetOSVERSION");
    public static final ID PJCVERSION      = ID.registerProperty("GetPJCVERSION");
    public static final ID JAVAOS          = ID.registerProperty("GetJAVAOS");
    public static final ID JAVAVERSION     = ID.registerProperty("GetJAVAVERSION");
    public static final ID JAVATEMPDIR     = ID.registerProperty("GetJAVATEMPDIR");   
    /* --- EO version & logger  --- */
   
    /* --- BO Parameter values --- */
    public static final String EOWATCH             = "EOWATCH";
    public static final String WATCHSERVERREADY    = "WatchServerReady";
    public static final String WATCHSERVERNOTREADY = "WatchServerNotReady";
    /* --- EO Parameter values  --- */  
    /* --- EO Forms PJC section --- */
    
    public static final Logger LOGGER = Logger.getLogger(FileWatcherPJC.class.getName());

    private String              mSubDir;
    private String              mWatchDir;
    private transient Path      mWatchPath;

    // watcher root dir
    private static final String FORMSTEMPDIR = "formswatch\\";
    private static final String DEFAULTSUBDIR  = "forms";
    
    /* specify file types for watching                         */
    /* for more types you can extend this in isCorrectFileType */
    private static final String WATCHERTHREAD = "WinWatcherPJC";
    private static final String WATCHTYPE     = "watch";
    private static final String FORMSTYPE     = "form";
    private static final String OTHERTYPE     = "other";
    
    /* watcher files */
    private static final String ACTION2FORMS  = "Action2Forms.watch";
    private static final String RESULT2FORMS  = "Result2Forms.watch";
    private static final String EOSERVICE     = "EOwatchService.watch";
    private static final String ACTION2OTHERS = "Action2Others.watch";
    private static final String RESULT2OTHERS = "Result2Others.watch";
    
    /* module variables */
    private  transient volatile  IHandler mHandler;
    
    private  transient volatile  Thread   mRunnerThread = null;
    private  volatile boolean             mPauseThread  = false;
    private  volatile boolean             mKillThread   = false;

    private boolean                       mBmsg         = false;
    private String                        mMessage      = "";
    private final transient Object        mPausedLock   = new Object();

    @Override
    public void init(IHandler handler) {
        if (handler != null) {
            mHandler = handler;
            super.init(handler);
            Ilog.setLogLevel(Level.FINER);
            Ilog.logInfo("--- EO-Init(IHandler) ---");
        }
    }                                                   

    @Override 
    public boolean setProperty(ID property, Object value) {
        String pStr = property.toString();
        String val = (String) value;
        if (SETSTARTSERVER.getName().equalsIgnoreCase(pStr)) {
            mBmsg = false;
            mMessage = "";
            if (!((val == null) || (val == " ") || (val == ""))) {
                mSubDir = val;
            } else {
                mSubDir = DEFAULTSUBDIR;
            }
            Ilog.logInfo("--- SETSTARTSERVER selected --- : "+ val);
            startThread();
            return true;
        } // EO SETSTARTSERVER
        else if (SETSTOPSERVER.getName().equalsIgnoreCase(pStr)) {
            // parameter EOWATCH OR empty
            if ((val.equalsIgnoreCase(EOWATCH))
                || (val == null) || (val == " ") || (val == "")) {
                // --------------------------------------------------------
                // stop service per "EOwatchService.watch" - file watching
                // at first stop watching then from there stopThread() !   
                // --------------------------------------------------------  
                Ilog.logFine("--- SETSTOPSERVER selected --- : "+ "val=\'" +val+ "\'");
                mPauseThread = true; // stop Thread !
                writeEOservice();     // <<< SENSE <<<
            } else {
                Ilog.logError("$$$ SETSTOPSERVER(parameter: EOWATCH ) $$$: val=\'"+ val +"\'");
            } // EO parameter EOWATCH
            return true;
        } // EO SETSTOPSERVER
        else if (SETKILLSERVER.getName().equalsIgnoreCase(pStr)) {
            try {
                Ilog.logFine("--- SETKILLSERVER selected ---");
                killThread();
            } catch (IOException e) {
                Ilog.logException("$$$ SETKILLSERVER $$$", e);
            }
            return true;
        } // EO SETKILLSERVER
        else if (SENDACTION2FORMS.getName().equalsIgnoreCase(pStr)) {
            if (val != null) {
                Ilog.logFine("--- SENDACTION2FORMS selected ---");
                sendFile(ACTION2FORMS, val);
            } else {
                Ilog.logError("$$$ SENDACTION2FORMS val is null ! $$$");
            }
            return true;
        } // EO SENDACTION2FORMS
        else if (SENDRESULT2FORMS.getName().equalsIgnoreCase(pStr)) {
            if (val != null) {
                Ilog.logFine("--- SENDRESULT2FORMS selected ---");
                sendFile(RESULT2FORMS, val);
            } else {
                Ilog.logError("$$$ SENDRESULT2FORMS val is null ! $$$");
            }
            return true;
        } // EO SENDRESULT2FORMS
        else if (SENDACTION2OTHERS.getName().equalsIgnoreCase(pStr)) {
            if (val != null) {
                Ilog.logFine("--- SENDACTION2OTHERS selected ---");
                sendFile(ACTION2OTHERS, val);
            } else {
                Ilog.logError("$$$ SENDACTION2OTHERS val is null ! $$$");
            }
            return true;
        } // EO SENDACTION2OTHERS
        else if (SENDRESULT2OTHERS.getName().equalsIgnoreCase(pStr)) {
            if (val != null) {
                Ilog.logFine("--- SENDRESULT2OTHERS selected ---");
                sendFile(RESULT2OTHERS, val);
            } else {
                Ilog.logError("$$$ SENDRESULT2OTHERS val is null ! $$$");
            }
            return true;
        } // EO SENDRESULT2OTHERS
        // set(msg) => back call => get(msg)
        else if (SETCHKGETMSG.getName().equalsIgnoreCase(pStr)) {
            Ilog.logInfo("--- SETCHKGETMSG(simulate Set&Get) ---");
            try {
                // set & get check.
                mHandler.setProperty(GETCHKMSG, "GETCHKMSG(simulate Set&Get)");
            } catch (FException e) {
                Ilog.logException("$$$ GETCHKMSG(simulate Set&Get) $$$", e);
            }
            CustomEvent ce = new CustomEvent(mHandler, EVENTCHKGETMSG);
            dispatchCustomEvent(ce);
            return true;
        } // EO SETCHKGETMSG
        else if (SETLOGGEROFF.getName().equalsIgnoreCase(pStr)) {
            Ilog.setLogOFF();
            return true;        
        } // EO SETLOGGEROFF
        else {
            Ilog.logError("$$$ SetProperty - Parameter: "+ pStr);
            return super.setProperty(property, value);
        }
    }
    
    /**
     * @param property
     * @return
     */
    @Override
    public String getProperty(ID property) {
        String pStr = property.toString();
        if (OS.getName().equalsIgnoreCase(pStr)) {
            return Version.getOS();
        } else if (OSVERSION.getName().equalsIgnoreCase(pStr)) {
            return Version.getOSVersion();
        } else if (PJCVERSION.getName().equalsIgnoreCase(pStr)) {
            return Version.getPJCVersion();
        } else if (JAVAOS.getName().equalsIgnoreCase(pStr)) {
            return Version.getJavaOS();
        } else if (JAVAVERSION.getName().equalsIgnoreCase(pStr)) {
            return Version.getJavaVersion();
        } else if (JAVATEMPDIR.getName().equalsIgnoreCase(pStr)) {
            return Version.getJavaTempDir();
        }
        // --- pid not found ! ---
        Ilog.logError(DEFAULTMESSAGE + " : " + property.toString());
        return DEFAULTMESSAGE + " : " + property.toString();
    }

    // starts or resume the thread here one time !
    private void startThread() {
        if (mRunnerThread == null) {
            mRunnerThread = new Thread(this, WATCHERTHREAD+ mSubDir);
            mPauseThread = false;
            mKillThread  = false;
            mRunnerThread.start();
            Ilog.logFine("--- New Watcher thread started ! ---");
        } else {
            // --- restarted ---
            Ilog.logFine("--- Watcher thread re-started ! ---");
            resumeThread();
        }
    }

    // stops the thread here
    private void stopThread() throws IOException, InterruptedException {
        
        if (mRunnerThread != null) {
            Thread.sleep(1000);
            pauseThread();
            Ilog.logFine("--- Watcher thread stoped ! ---");
        } else {
            Ilog.logFine("--- Watcher thread not stoped, m_runnerThread is null ! ---"); 
        }
    }
    
    // command from extern !
    private void killThread() throws IOException {
        if (mRunnerThread != null) {            
            synchronized(this) {  
                Ilog.logFine("--- killThread() - m_runner.getState() :: " + 
                          mRunnerThread.getState() + " / " + mPauseThread);                       
                // --------------------------------------------------------
                // stop service per "EOwatchService.watch" - file watching
                // at first stop watching then from killThread() there !   
                // --------------------------------------------------------  
                writeEOservice();    // <<< SENSE <<<                       
                mKillThread = true; // stop polling !        
            }    
            mRunnerThread = null;         
            Ilog.logFiner("--- killing Thread --- ");
        } else {
            Ilog.logFine("--- killing Thread : m_runnerThread is null ! --- "); 
        }
    }
        
    private void pauseThread() {
        synchronized (mPausedLock) {
            if (Thread.currentThread().getName().equalsIgnoreCase(WATCHERTHREAD)) {
                mPauseThread = true;     // stop polling !
                while (mPauseThread) {
                    try {
                        Ilog.logFine("--- pauseThread ---");
                        mPausedLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private void resumeThread() {
        synchronized (mPausedLock) {
            if (State.WAITING.equals(mRunnerThread.getState())) {
                Ilog.logFine("--- resumeThread ---");
                mPauseThread = false;
                mPausedLock.notifyAll();
            }
        }
    }
    
    private boolean getServerState() {
        return (mRunnerThread != null) && (!mPauseThread);
    }

    private void writeEOservice() {   
        try {
            // --------------------------------------------------------
            // stop service per "EOwatchService.watch" - file watching
            // at first stop watching then from there killThread() !   
            // --------------------------------------------------------
            Ilog.logFine("--- SETSTOPSERVER :: EOwatchService.watch selected. ---");
                FileWatcherPJC.wrtFile(mWatchDir + "\\" + EOSERVICE, "FINISHED.");
            } catch (IOException e) {
                Ilog.logException("$$$ SETSTOPSERVER(wrtFile() => EOwatch) $$$", e);
            }
    }
    
    // send watch message back to Forms for trigger event
    private void sendWatch2Forms(String wMessage) {
        try {
            if (!(mHandler == null)) {
                Ilog.logFine("--- :: SendWatch2Forms:" + wMessage);
                mHandler.setProperty(GETMSG, wMessage);
                CustomEvent ce = new CustomEvent(mHandler, EVENTGETMSG);
                dispatchCustomEvent(ce);
            } else {
                Ilog.logError("$$$ SendWatch2Forms(m_Handler==null) $$$: " + wMessage);
            }
        } catch (Exception e) {
            Ilog.logException("$$$ SendWatch2Forms $$$: " + wMessage, e);
        }
    }

    // send action back to Forms for trigger event
    private synchronized void sendAction2Forms(String paras) {
        try {
            if (!(mHandler == null)) {
                Ilog.logFine("--- :: SendAction2Forms:" + paras);
                mHandler.setProperty(GETACTIONPARAS, paras);
                CustomEvent ce = new CustomEvent(mHandler, EVENTACTION);
                dispatchCustomEvent(ce);
            } else {
                Ilog.logError("$$$ SendAction2Forms(m_Handler==null) $$$: " + paras);
            }
        } catch (Exception e) {
            Ilog.logException("$$$ SendAction2Forms $$$: " + paras, e);
        }
    }

    // fname ACTION|Para1|Para2
    private void action2Forms(String fname) throws IOException {
        try {
            FileInputStream inpStream = new FileInputStream(mWatchDir + fname);
            BufferedReader bufRead = new BufferedReader(
                    new InputStreamReader(inpStream));
            String input = bufRead.readLine();
            sendAction2Forms(input);          
            inpStream.close();
            Ilog.logFine("--- EO action2Forms(String fname) ---");           
        } catch (IOException e) {
            Ilog.logException("$$$ Action2Forms(InputStream) $$$ : " + fname, e);
        }
    }

    // send result back to Forms for trigger event
    private synchronized void sendResult2Forms(String paras) {
        try {
            if (!(mHandler == null)) {
                Ilog.logFine("--- :: SendAction2Forms:" + paras);
                mHandler.setProperty(GETRESULTPARAS, paras);
                CustomEvent ce = new CustomEvent(mHandler, EVENTRESULT);
                dispatchCustomEvent(ce);
                Ilog.logFine("--- EO sendResult2Forms() ---");
            } else {
                Ilog.logError("$$$ SendAction2Forms(m_Handler==null) $$$: " + paras);
            }
        } catch (Exception e) {
            Ilog.logException("$$$ SendAction2Forms $$$: " + paras, e);
        }
    }

    // fname ACTION|Para1|Para2
    private void result2Forms(String fname) throws IOException {
        try {
            FileInputStream inpStream = new FileInputStream(mWatchDir + fname);
            BufferedReader bufRead = new BufferedReader(
                    new InputStreamReader(inpStream));
            String input = bufRead.readLine();
            sendResult2Forms(input);
            inpStream.close();
            Ilog.logFine("--- EO result2Forms(String fname) ---");
        } catch (IOException e) {
            Ilog.logException("$$$ Result2Forms(InputStream) $$$ : " + fname, e);
        }
    }

    // send Action to destination subdir : action|result
    private static synchronized void sendFile(String type, String value) {
        String[] split;
        split = value.split("\\|", 2);
        String dir;
        try {
            dir = System.getProperty("java.io.tmpdir")+FORMSTEMPDIR+"\\"+split[0];
            OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(dir + "\\" + type), "UTF-8");
            // Timestamp "timestamp = new Timestamp(System.currentTimeMillis());"
            writer.write(split[1]); 
            writer.close();
            Ilog.logFine("--- EO sendFile(String type, String value) ---");
        } catch (IOException e) {
            Ilog.logException("$$$ sendFile(String type, String value) $$$ : " + type+"|"+ value, e);
        }
    } 
    
    private static synchronized void createDir(String directory) {
        boolean bool = false;
        try {
            if (directory != null) {
                File localDir = new File(directory);
                if (!localDir.exists()) {
                    bool = localDir.mkdirs();
                    if (!bool) {
                        Ilog.logError("$$$ createDir(mkdirs) $$$ : " + directory);
                    }
                }
                Ilog.logFine("--- EO createDir(String directory) ---");
            }
        } catch (Exception e) {
            Ilog.logException("$$$ createDir $$$ : " + directory, e);
        }
    }

    private static synchronized void wrtFile(String fname, String msg) throws IOException {
        try {
            OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(fname), "UTF-8");
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            writer.write(timestamp + "\n" + msg);
            writer.close();
            Ilog.logFine("--- EO wrtFile(String fname, String msg) ---");
        } catch (IOException e) {
            Ilog.logException("$$$ wrtFile $$$ : " + fname, e);
        }
    }

    private boolean isCorrectFileType(Path file) {
        return  file.toString().endsWith(WATCHTYPE) ||
                file.toString().endsWith(FORMSTYPE) ||
                file.toString().endsWith(OTHERTYPE);
    }
 
    // Start the thread watcher
    @Override
    public void run() {
        // "Thread theThread = Thread.currentThread();"

        String tContext;
        Path tFile;

        while (mRunnerThread == Thread.currentThread()) {
            
            WatchService watchService = null;
            // we need sleep to release processor for do any thing else         
            try {
                watchService = FileSystems.getDefault().newWatchService();
                // watching dir handling.
                mWatchDir = System.getProperty("java.io.tmpdir");
                mWatchDir = mWatchDir + FORMSTEMPDIR;
                if (mSubDir != "") {
                    mWatchDir = mWatchDir + mSubDir + "\\";
                }
                // get final path.
                createDir(mWatchDir);
                Ilog.logInfo("WatchDir: " + mWatchDir);
                mWatchPath = Paths.get(mWatchDir);

                // <delete> & <create files> does always include <modify files>!
                WatchKey key = mWatchPath.register(watchService,
                        // StandardWatchEventKinds.ENTRY_CREATE,
                        // StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                // -- main watcher inner loop ---
                while ((!mPauseThread)&&(!mKillThread)) {
                    Ilog.logInfo("--- ENTER BO watchService.take() ---");                    
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        Ilog.logException("$$$ interrupted watchService (watchService.take()) $$$: ", e);
                        Thread.currentThread().interrupt();
                    }
                    // delay latch <<< !!!
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Ilog.logException("$$$ interrupted watchService (Thread.sleep(100)) $$$: ", e);
                        Thread.currentThread().interrupt();
                    }

                    List<WatchEvent<?>> keys = key.pollEvents();
                    
                    for (WatchEvent<?> watchEvent : keys) {
                        Kind<?> watchEventKind = watchEvent.kind();
                        tContext = watchEvent.context().toString();
                        tFile = mWatchPath.resolve(tContext);
                        if (watchEventKind == StandardWatchEventKinds.OVERFLOW) {
                            sendWatch2Forms("$$$ File event overflow $$$:" + tContext);
                            continue;
                        } 
                        //  --- only modified and filtered files . ---
                        if  ((isCorrectFileType(tFile)) &&
                             (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) &&
                             (tContext != null)) {                                 
                            switch (tContext) {
                                case ACTION2FORMS:                                                
                                    action2Forms(ACTION2FORMS);                              
                                    break;  // break EO "fors (WatchEvent<?>"
                                    // result of actions
                                case RESULT2FORMS:
                                    result2Forms(RESULT2FORMS);
                                    break;  // break EO "fors (WatchEvent<?>"
                                    // check file name => stop watching.
                                case EOSERVICE:
                                    mMessage = EOSERVICE;
                                    Ilog.logInfo("--- EOwatchService.watch :: CLOSE watchservice ---");
                                    break;  // break EO "fors (WatchEvent<?> => killThread"                                                  
                                default:
                                    sendWatch2Forms("File-modified::" + tContext);
                                    break;  // break EO "fors (WatchEvent<?>"                                  
                            }                                 
                        } // EO (isCorrectFileType(t_file))
                        
                        // reset latched WatchEvent    
                        key.reset();
                        
                        // check to close watching service ..
                        if ((mKillThread)||(mPauseThread)) {
                           break;
                        } 
                     
                    } // EO "fors (WatchEvent<?>)"

                } // EO "whiles (m_Polling)" --- main watcher loop ---
                                            
                watchService.close();
                Ilog.logFine("--- watchService.closed ---");
                                
            } catch (IOException e) {
                Ilog.logException("$$$ watchService $$$: ", e);
            }   // EO try/catch : watchservice .
                           
            // --- tell Forms that a message is incoming ---
            if (mBmsg) {
                Ilog.logFine("--- Sendmessage= " + mMessage);
                sendWatch2Forms(mMessage);
                mBmsg = false;
                if (mMessage.equalsIgnoreCase(EOSERVICE)) {
                    try {
                        stopThread();
                    } catch (IOException | InterruptedException e) {
                        Ilog.logException("$$$ stopThread() $$$: " + mMessage, e);
                    }
                }
            }   
            
            Ilog.logFinest("§§§ BEFORE LAST break ! §§§");
            
            if (mKillThread) {
                Ilog.logFine("--- Thread killing() :: RETURN . ---");
                break;  // >>> kill Thread !
                
            } else if (mPauseThread) {
                try {
                    Ilog.logFine("--- Thread stopping() :: WAIT . ---");
                    stopThread();
                } catch (IOException | InterruptedException e) {
                    Ilog.logException("$$$ Thread sleep() $$$", e);
                } 
            } else {
                Ilog.logError("$$$ NOT defined state !!! $$$");    
            }                  
        } // EO "whiles (m_runner == Thread.currentThread())"
        
        Ilog.logFine("§§§ Thread EO run. $$$");
        
    }  // EO run

}
