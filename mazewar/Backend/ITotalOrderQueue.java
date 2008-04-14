package Backend;

import java.io.IOException;

interface ITotalOrderQueue
{
    public abstract void queueIncomingMessage (PeerMessage msg, int acks)
        throws IOException, ClassNotFoundException;

    public abstract void dispatchMessageQueue ()
        throws IOException, ClassNotFoundException;

    public abstract void ackIncomingMessage (PeerACK ack)
        throws IOException, ClassNotFoundException;

    public abstract void ackIncomingMessage (PeerACK ack, GlobalTimestamp queueKey)
        throws IOException, ClassNotFoundException;
}
