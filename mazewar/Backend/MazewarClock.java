package Backend;

import java.io.Serializable;

class GlobalTimestamp implements Comparable, Serializable
{
    public static final GlobalTimestamp Undefined = new GlobalTimestamp(new PeerLocation("1.2.3.4", -1), -1);

    private int _Time;
    private final PeerLocation _PID;

    protected GlobalTimestamp (PeerLocation pid, int time)
    {
        _PID = pid;
        _Time = time;
    }

    public boolean equals (GlobalTimestamp rhs)
    {
        return compareTo(rhs) == 0;
    }

    public int compareTo (Object rhs)
    {
        GlobalTimestamp gts = (GlobalTimestamp)rhs;

        if (_Time != gts._Time)
        {
            return _Time - gts._Time;
        }
        else
        {
            return _PID.compareTo(gts._PID);
        }
    }

    public String toString()
    {
        if (this.equals(Undefined))
            return "TS(Undefined)";
        else
            return "TS(" + _PID.toString() + ", " + Integer.toString(_Time) + ")";
            //return "TS(" + Integer.toString(_Time) + ")";
    }

    public synchronized GlobalTimestamp getNewMessageTime ()
    {
        _Time++;

        return new GlobalTimestamp(_PID, _Time);
    }

    public synchronized GlobalTimestamp getTime ()
    {
        return new GlobalTimestamp(_PID, _Time);
    }

    public synchronized void syncMessageTime (GlobalTimestamp receivedTS)
    {
        int oldTime = _Time;

        _Time = Math.max(_Time, receivedTS._Time);
    }
}

public class MazewarClock extends GlobalTimestamp
{
    MazewarClock (PeerLocation me)
    {
        super(me, 0);
    }
}
