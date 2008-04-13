package Backend;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentSkipListMap;
import java.io.IOException;

class QueueRecord
{
    public final GlobalTimestamp Timestamp;
    public final PeerMessage Message;

    private int AcksLeft;

    public QueueRecord (PeerMessage msg, int acks)
    {
        Timestamp = msg.Timestamp;
        Message = msg;
        AcksLeft = acks;
    }

    public synchronized boolean isAcknowledged ()
    {
        return (AcksLeft == 0);
    }

    public synchronized boolean acknowledge ()
    {
        if (isAcknowledged())
            throw new IllegalStateException("Message already fully acknowledged.");

        AcksLeft--;

        return isAcknowledged();
    }

    public String toString()
    {
        return "[" + Timestamp.toString() + ", " + Integer.toString(AcksLeft) + "] " + Message.toString();
    }
}

public class MazewarTotalOrderQueue implements ITotalOrderQueue
{
    // Message queue.
    private final SortedMap<GlobalTimestamp, QueueRecord> _Queue = Collections.synchronizedSortedMap(new TreeMap<GlobalTimestamp, QueueRecord>());

    // Who gets our messages.
    private final ITOMRecipient _Target;

    public MazewarTotalOrderQueue (ITOMRecipient target)
    {
        _Target = target;
    }

    public void queueIncomingMessage (PeerMessage msg, int acks)
        throws IOException, ClassNotFoundException
    {
        QueueRecord qr = new QueueRecord(msg, acks);

        Log.Write("Queuing message: " + qr.toString());

        _Queue.put(qr.Timestamp, qr);

        dispatchMessageQueue ();
    }

    public synchronized void ackIncomingMessage (PeerACK ack)
        throws IOException, ClassNotFoundException
    {
        // What message is this trying to ACK?
        if (!_Queue.containsKey(ack.OriginalTimestamp))
            throw new IllegalArgumentException("ERROR: Client ACK for unknown timestamp.");

        _Queue.get(ack.OriginalTimestamp).acknowledge();

        dispatchMessageQueue();
    }

    public synchronized void dispatchMessageQueue ()
        throws IOException, ClassNotFoundException
    {
        QueueRecord head = _Queue.get(_Queue.firstKey());
        while (head.isAcknowledged())
        {
            Log.Write("Dispatching message at head of queue: " + head.Message.toString());
            
            _Target.dispatchMessage(head.Message);
            
            // OFF WITH ITS HEAD.
            _Queue.remove(_Queue.firstKey());

            head = _Queue.get(_Queue.firstKey());
        }
    }

}
