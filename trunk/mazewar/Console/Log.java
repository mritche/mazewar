public class Log
{
    public static synchronized void Write (String msg)
    {
        System.out.println("[" + Thread.currentThread().getName() + "] " + msg);
    }
}
