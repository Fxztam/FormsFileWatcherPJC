package fwatch.pjc.fm;

/**
 * @author Friedhold Matz, October 2017.
 * 
 */

import java.io.PrintWriter; 
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import oracle.forms.properties.ID;

/* --- Forms PJC class ---*/
public class Ilog {

    public  static final  Logger  LOGGER   = Logger.getGlobal();  
        
    private static        Level   logLevel  = Level.FINE; 
    private static final  String  CLASSNAME = Ilog.class.getName();
    
    static {
        // remove the default handlers        
        Logger rootLogger = Logger.getLogger("");
        Handler[] rootHandlers = rootLogger.getHandlers();
        for (Handler handler : rootHandlers) {
            rootLogger.removeHandler(handler);
        }  
        ConsoleHandler handler = new ConsoleHandler();     
        handler.setLevel(logLevel);        
        handler.setFormatter(new LogFormatter());    
        LOGGER.addHandler(handler);
        LOGGER.setLevel(logLevel);
    }
 
    public static class LogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String stackTrace = "";
            Throwable thrown = record.getThrown();
            if (thrown != null) {
                StringWriter stacktraceWriter = new StringWriter();
                try (PrintWriter writer = new PrintWriter(stacktraceWriter)) {
                    thrown.printStackTrace(writer);
                }
                stackTrace = stacktraceWriter.toString();
            }                  
            LocalDateTime ldt = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

            return  ldt.format(formatter) +                   
                    " "  + record.getLevel() +
                    " "  + record.getMessage() +
                    " ...{Thread:"  + Thread.currentThread().getName() + " : " +
                                      Thread.currentThread().getId()   + "} "  +
                    "\n" + stackTrace;
        }
    }
       
    private static String getCallerRef() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length < 4) {
            return "";
        } else {
            int i = 1;
            for (; i < stackTraceElements.length; i++) {
                if (stackTraceElements[i].getClassName().equals(CLASSNAME)) {
                    break;
                }
            }
            for (; i < stackTraceElements.length; i++) {
                if (!stackTraceElements[i].getClassName().equals(CLASSNAME)) {
                    break;
                }
            }
            if (i < stackTraceElements.length) {
                return stackTraceElements[i].toString();
            } else {
                return "[??? unknown method]";
            }
        }
    }
         
    public static void setLogLevel(Level newLogLevel) {
        logLevel = newLogLevel;
        for (Handler handler : LOGGER.getHandlers()) {
            handler.setLevel(newLogLevel);
        }
        Ilog.LOGGER.setLevel(newLogLevel);
    }

    public static void setLogOFF() {
        Ilog.LOGGER.setLevel(Level.OFF);
    } 
    public static int getLogLevelNum(Level level) {
        return level.intValue();
    }   
    public static void logFinest(String msg) {
        LOGGER.log(Level.FINEST, msg);
    }   
    public static void logFiner(String msg) {
        LOGGER.log(Level.FINER, msg);
    }   
    public static void logFine(String msg) {
        LOGGER.log(Level.FINE, msg);
    }
    public static void logInfo(String msg) {
        LOGGER.log(Level.INFO, msg);
    }
    public static void logWarning(String msg) {
        LOGGER.log(Level.WARNING, msg+"\t" + getCallerRef());
    }
    public static void logError(String msg) {
        LOGGER.log(Level.SEVERE, "[ERROR] "+msg+"\t "+ getCallerRef());
    }
    public static void logException(String msg, Throwable cause) {
        LOGGER.log(Level.SEVERE, "[EXCEPTION] "+ msg +"\t "+ getCallerRef(), cause);
    }

}
