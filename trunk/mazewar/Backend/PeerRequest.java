package Backend;

import java.io.Serializable;

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
		return Host.equals(rhs.Host) && (Port.intValue() == rhs.Port.intValue());
	}

    public String toString ()
    {
        return Host + ":" + Port.toString();
    }

    public int compareTo (Object o)
    {
        PeerLocation rhs = (PeerLocation)o;

        if (!Host.equals(rhs.Host))
        {
            return Host.compareTo(rhs.Host);
        }
        else
        {
            return Port - rhs.Port;
        }
    }
}

enum PeerMessageID
{
    HELLO,
    GOODBYE,
    EMPTY,
    
    DERIVED
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
        MessageID = PeerMessageID.DERIVED;
    }

    PeerMessage (PeerLocation src)
    {
        Source = src;
        Timestamp = GlobalTimestamp.Undefined;
        MessageID = PeerMessageID.DERIVED;
    }

    PeerMessage (PeerLocation src, GlobalTimestamp ts, PeerMessageID msg)
    {
        Source = src;
        Timestamp = ts;
        MessageID = msg;
    }

    public String toString()
    {
        return "(PeerMessage, " + Source.toString() + ", " + MessageID.toString() + ", " + Timestamp.toString() + ")";
    }
}

class PeerClientMessage extends PeerMessage implements Serializable
{
    public final Object Data;

    PeerClientMessage (PeerLocation src, Object o)
    {
        super(src);

        Data = o;
    }

    public String toString()
    {
        return "(PeerClientMessage, " + Source.toString() + ", " + Timestamp.toString() + ", " + ((Data != null) ? Data.toString() : "{null}") + ")";
    }
}

class PeerACK extends PeerMessage implements Serializable
{
    // The timestamp of the message we're ACKing. ACK ACK ACK. This is ACKtastic.
    public GlobalTimestamp OriginalTimestamp;

    PeerACK (PeerLocation src, PeerMessage original)
    {
        super(src);

        OriginalTimestamp = original.Timestamp;
    }

    PeerACK (PeerLocation src, GlobalTimestamp ts, PeerMessage original)
    {
        super(src, ts);
        
        OriginalTimestamp = original.Timestamp;
    }

    public String toString()
    {
        return "(PeerACK, " + Source.toString() + ", " + Timestamp.toString() +  ", ORIGINAL_TIME = " + OriginalTimestamp.toString() + ")";
    }
}

class PeerClientACK extends PeerACK implements Serializable
{
    PeerClientACK (PeerLocation src, PeerMessage original)
    {
        super(src, original);
    }

    PeerClientACK (PeerLocation src, GlobalTimestamp ts, PeerMessage original)
    {
        super(src, ts, original);
    }
}

class NewPeerACK extends PeerACK implements Serializable
{
    public Object InitialState;

    NewPeerACK (PeerLocation src, PeerMessage original)
    {
        super(src, original);

        InitialState = null;
    }

    NewPeerACK (PeerLocation src, PeerMessage original, Object state)
    {
        super(src, original);

        InitialState = state;
    }

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

    public String toString()
    {
        return "(NewPeerACK, " + Source.toString() + ", " + Timestamp.toString() + ", ORIGINAL_TIME = " + OriginalTimestamp.toString() + ", InitialState = " + ((InitialState != null) ? InitialState.toString() : "{null}") + ")";
    }
}

class NewPeerMessage extends PeerMessage implements Serializable
{
    public final Integer PeerCount;

    public NewPeerMessage (PeerLocation src, int peerCount)
    {
        super(src);

        PeerCount = peerCount;
    }

    public NewPeerMessage (PeerLocation src, GlobalTimestamp ts, int peerCount)
    {
        super(src, ts);

        PeerCount = peerCount;
    }

    public String toString()
    {
        return "(NewPeer, " + Source.toString() + ", " + Timestamp.toString() + ", " + PeerCount.toString() + ")";
    }
}
