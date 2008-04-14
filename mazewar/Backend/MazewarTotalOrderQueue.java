package Backend;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;
import java.io.IOException;

class QueueRecord
{
    public final GlobalTimestamp Timestamp;
    public PeerMessage Message;

    private int AcksLeft;

    private int AcksSeenBeforeMessage = 0;

    public QueueRecord (PeerMessage msg, int acks)
    {
        Timestamp = msg.Timestamp;
        Message = msg;
        AcksLeft = acks;
    }

    public QueueRecord (GlobalTimestamp ts)
    {
        Timestamp = ts;
        Message = null;
        AcksLeft = -1;
    }

    public synchronized void associateWithMessage (PeerMessage msg, int acks)
    {
        Message = msg;
        AcksLeft = acks - AcksSeenBeforeMessage;
        AcksSeenBeforeMessage = 0;
    }

    public synchronized boolean isAcknowledged ()
    {
        return (AcksLeft == 0);
    }

    public synchronized boolean acknowledge ()
    {
        if (isAcknowledged())
            throw new IllegalStateException("Message already fully acknowledged.");

        if (Message == null)
        {
            AcksSeenBeforeMessage++;
        }
        else
        {
            AcksLeft--;
        }
        
        return isAcknowledged();
    }

    public String toString()
    {
        return "[" + Timestamp.toString() + ", AcksLeft=" + Integer.toString(AcksLeft) + "] ";
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

    public synchronized void queueIncomingMessage (PeerMessage msg, int acks)
        throws IOException, ClassNotFoundException
    {
        QueueRecord qr;

        if (!_Queue.containsKey(msg.Timestamp))
        {
            qr = new QueueRecord(msg, acks);

            _Queue.put(qr.Timestamp, qr);
        }
        else
        {
            qr = _Queue.get(msg.Timestamp);

            qr.associateWithMessage(msg, acks);
        }

        dispatchMessageQueue ();
    }

    public synchronized void ackIncomingMessage (PeerACK ack, GlobalTimestamp queueKey)
        throws IOException, ClassNotFoundException
    {
        // If we see the ACK before the message, account for that.
        if (!_Queue.containsKey(queueKey))
        {
             _Queue.put(queueKey, new QueueRecord(queueKey));
        }

        QueueRecord qr = _Queue.get(queueKey);

        //Log.Write("Acknowledging message: " + qr.toString());
        
        qr.acknowledge();

        dispatchMessageQueue();
    }

    public synchronized void ackIncomingMessage (PeerACK ack)
        throws IOException, ClassNotFoundException
    {
        ackIncomingMessage(ack, ack.OriginalTimestamp);
    }

    public synchronized void dispatchMessageQueue ()
        throws IOException, ClassNotFoundException
    {
        dispatchMessageQueueInt();
    }

    public synchronized int dispatchMessageQueueInt ()
        throws IOException, ClassNotFoundException
    {
        QueueRecord head = null;
        if (_Queue.isEmpty())
            return 0;

        ArrayList<PeerMessage> toDispatch = new ArrayList<PeerMessage>();

        while (!_Queue.isEmpty()
                && (head = _Queue.get(_Queue.firstKey())) != null
                && head.isAcknowledged())
        {
            PeerMessage msg = head.Message;

            // OFF WITH ITS HEAD.
            QueueRecord removed = _Queue.remove(_Queue.firstKey());

            //Log.Write("Removed message at head of queue (size = " + _Queue.size() + "): " + removed.Message.toString());

            toDispatch.add(msg);
        }

        for (PeerMessage msg : toDispatch)
        {
            _Target.dispatchMessage(msg);
        }

        return toDispatch.size();
    }

}
