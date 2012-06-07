package helloc.utils;

public class Logger
{
    public enum Level
    {
        ERROR, WARNING, INFO, DEBUG
    }

    public interface Loggable
    {
        void log(Level level, String category, String msg);
    }

    static Loggable logger;

    public static void setLogVendor(Loggable l)
    {
        logger = l;
    }

    public static void log(Level level, String category, String msg)
    {
        if(logger != null)
                logger.log(level, category, msg);
            else
        System.err.printf("%s: [%s] %s\n", level, category, msg);
    }

    public static void d(String category, String msg)
    {
        log(Level.DEBUG, category, msg);
    }

    public static void i(String category, String msg)
    {
        log(Level.INFO, category, msg);
    }

    public static void w(String category, String msg)
    {
        log(Level.WARNING, category, msg);
    }

    public static void e(String category, String msg)
    {
        log(Level.ERROR, category, msg);
    }

    public static void d(String msg)
    {
        log(Level.DEBUG, "Default", msg);
    }

    public static void i(String msg)
    {
        log(Level.INFO, "Default", msg);
    }

    public static void w(String msg)
    {
        log(Level.WARNING, "Default", msg);
    }

    public static void e(String msg)
    {
        log(Level.ERROR, "Default", msg);
    }
}