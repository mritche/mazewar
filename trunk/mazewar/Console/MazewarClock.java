
public class MazewarClock
{
    private int _Time;

    MazewarClock ()
    {
        _Time = 0;
    }

    public synchronized int getTime ()
    {
        return _Time;
    }

    public synchronized int getNewMessageTime ()
    {
        _Time++;

        return _Time;
    }

    public synchronized void syncMessageTime (int receivedTime)
    {
        _Time = Math.max(_Time, receivedTime);
    }
}
