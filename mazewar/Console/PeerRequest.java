import java.io.Serializable;
import java.util.ArrayList;

class PeerLocation implements Serializable
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
    public Integer Timestamp;
    public final PeerLocation Source;
    public final PeerMessageID MessageID;

    PeerMessage (PeerLocation src, int ts)
    {
        Source = src;
        Timestamp = ts;
        MessageID = PeerMessageID.INVALID;
    }

    PeerMessage (PeerLocation src, int ts, PeerMessageID msg)
    {
        Source = src;
        Timestamp = ts;
        MessageID = msg;
    }

    public String toString()
    {
        return "(" + this.getClass().toString() + ", " + Source.toString() + ", " + MessageID.toString() + ")";
    }
}

class PeerClientMessage extends PeerMessage implements Serializable
{
    public final Object Data;

    PeerClientMessage (PeerLocation src, int ts, Object o)
    {
        super(src, ts);

        Data = o;
    }
}

class PeerACK extends PeerMessage implements Serializable
{
    // The timestamp of the message we're ACKing. ACK ACK ACK. This is ACKtastic.
    public Integer OriginalTimestamp;

    PeerACK (PeerLocation src, int ts, PeerMessage original)
    {
        super(src, ts);
        
        OriginalTimestamp = original.Timestamp;
    }

    public String toString()
    {
        return "(" + this.getClass().toString() + ", " + Source.toString() + ", ORIGINAL_TIME = " + OriginalTimestamp.toString() + ")";
    }
}

class PeerClientACK extends PeerACK implements Serializable
{
    PeerClientACK (PeerLocation src, int ts, PeerMessage original)
    {
        super(src, ts, original);
    }
}

class NewPeerACK extends PeerACK implements Serializable
{
    public final Object InitialState;

    NewPeerACK (PeerLocation src, int ts, PeerMessage original)
    {
        super(src, ts, original);

        InitialState = null;
    }

    NewPeerACK (PeerLocation src, int ts, PeerMessage original, Object state)
    {
        super(src, ts, original);

        InitialState = state;
    }
}

class NewPeerMessage extends PeerMessage implements Serializable
{
    public final Integer PeerCount;

    public NewPeerMessage (PeerLocation src, int ts, int peerCount)
    {
        super(src, ts);

        PeerCount = peerCount;
    }

    public String toString()
    {
        return "(" + this.getClass().toString() + ", " + Source.toString() + ", " + MessageID.toString() + ", " + PeerCount.toString() + ")";
    }
}
