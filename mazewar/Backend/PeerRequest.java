package Backend;

import java.io.Serializable;
import java.util.ArrayList;

class PeerLocation implements Serializable, Comparable
{
    public String  Host;
	public Integer Port;

	public PeerLocation (String host, Integer port)
    {
        Host = host;
        Port = port;
    }

	public boolean equals (PeerLocation rhs)
    {
		return Host.equals(rhs.Host) && Port.equals(rhs.Port);
	}

    public String toString ()
    {
        return Host + ":" + Port.toString();
    }

    public int compareTo (Object o)
    {
        PeerLocation rhs = (PeerLocation)o;

        return toString().compareTo(rhs.toString());
    }
}

enum PeerMessageID
{
    HELLO,
    GOODBYE,
    EMPTY,
    
    INVALID
}

class PeerMessage implements Serializable
{
    public GlobalTimestamp Timestamp;
    public final PeerLocation Source;
    public final PeerMessageID MessageID;

    PeerMessage (PeerLocation src, GlobalTimestamp ts)
    {
        Source = src;
        Timestamp = ts;
        MessageID = PeerMessageID.INVALID;
    }

    PeerMessage (PeerLocation src, GlobalTimestamp ts, PeerMessageID msg)
    {
        Source = src;
        Timestamp = ts;
        MessageID = msg;
    }

    public String toString()
    {
        return "(" + getClass().toString() + ", " + Source.toString() + ", " + MessageID.toString() + ")";
    }
}

class PeerClientMessage extends PeerMessage implements Serializable
{
    public final Object Data;

    PeerClientMessage (PeerLocation src, GlobalTimestamp ts, Object o)
    {
        super(src, ts);

        Data = o;
    }

    public String toString()
    {
        return "(" + getClass().toString() + ", " + Source.toString() + ", " + MessageID.toString() + ", " + Data.toString() + ")";
    }
}

class PeerACK extends PeerMessage implements Serializable
{
    // The timestamp of the message we're ACKing. ACK ACK ACK. This is ACKtastic.
    public GlobalTimestamp OriginalTimestamp;

    PeerACK (PeerLocation src, GlobalTimestamp ts, PeerMessage original)
    {
        super(src, ts);
        
        OriginalTimestamp = original.Timestamp;
    }

    public String toString()
    {
        return "(" + getClass().toString() + ", " + Source.toString() + ", " + Timestamp.toString() +  ", ORIGINAL_TIME = " + OriginalTimestamp.toString() + ")";
    }
}

class PeerClientACK extends PeerACK implements Serializable
{
    PeerClientACK (PeerLocation src, GlobalTimestamp ts, PeerMessage original)
    {
        super(src, ts, original);
    }
}

class NewPeerACK extends PeerACK implements Serializable
{
    public final Object InitialState;

    NewPeerACK (PeerLocation src, GlobalTimestamp ts, PeerMessage original)
    {
        super(src, ts, original);

        InitialState = null;
    }

    NewPeerACK (PeerLocation src, GlobalTimestamp ts, PeerMessage original, Object state)
    {
        super(src, ts, original);

        InitialState = state;
    }
}

class NewPeerMessage extends PeerMessage implements Serializable
{
    public final Integer PeerCount;

    public NewPeerMessage (PeerLocation src, GlobalTimestamp ts, int peerCount)
    {
        super(src, ts);

        PeerCount = peerCount;
    }

    public String toString()
    {
        return "(" + this.getClass().toString() + ", " + Source.toString() + ", " + MessageID.toString() + ", " + PeerCount.toString() + ")";
    }
}
