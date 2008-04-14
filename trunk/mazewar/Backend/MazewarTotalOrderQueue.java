package Backend;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentNavigableMap;
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
        if (isAcknowledged() && Message != null)
        {
            //throw new IllegalStateException("Message already fully acknowledged.");
            return true;
        }

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
    private final ConcurrentNavigableMap<GlobalTimestamp, QueueRecord> _Queue = new ConcurrentSkipListMap<GlobalTimestamp, QueueRecord>();

    // Who gets our messages.
    private final ITOMRecipient _Target;

    public MazewarTotalOrderQueue (ITOMRecipient target)
    {
        _Target = target;
    }

    public void queueIncomingMessage (PeerMessage msg, int acks)
        throws IOException, ClassNotFoundException
    {
        QueueRecord newRecord = new QueueRecord(msg, acks);
        QueueRecord oldRecord = _Queue.putIfAbsent(msg.Timestamp, newRecord);
        if (oldRecord != null)
        {
            oldRecord.associateWithMessage(msg, acks);
        }

        dispatchMessageQueue ();
    }

    public void ackIncomingMessage (PeerACK ack, GlobalTimestamp queueKey)
        throws IOException, ClassNotFoundException
    {
        // If we see the ACK before the message, account for that.
        QueueRecord newRecord = new QueueRecord(queueKey);
        QueueRecord oldRecord = _Queue.putIfAbsent(queueKey, newRecord);
        boolean doDispatch;
        if (oldRecord != null)
        {
            doDispatch = oldRecord.acknowledge();
        }
        else
        {
            doDispatch = newRecord.acknowledge();
        }

        //Log.Write("Acknowledging message: " + qr.toString());

        if (doDispatch)
            dispatchMessageQueue();
    }

    public void ackIncomingMessage (PeerACK ack)
        throws IOException, ClassNotFoundException
    {
        ackIncomingMessage(ack, ack.OriginalTimestamp);
    }

    public void dispatchMessageQueue ()
        throws IOException, ClassNotFoundException
    {
        dispatchMessageQueueInt();
    }

    public int dispatchMessageQueueInt ()
        throws IOException, ClassNotFoundException
    {
        ArrayList<PeerMessage> toDispatch = new ArrayList<PeerMessage>();

        synchronized (_Queue)
        {
            QueueRecord head = null;
            if (_Queue.isEmpty())
                return 0;

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
        }

        for (PeerMessage msg : toDispatch)
        {
            _Target.dispatchMessage(msg);
        }

        return toDispatch.size();
    }

}
