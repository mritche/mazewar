import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

class QueueRecord
{
    public final Integer Timestamp;
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

public class MazewarServer implements Runnable, INetworkServer
{
    public final PeerLocation Me;

    // L-L-L-L-Lamport!
    public final MazewarClock Clock = new MazewarClock();

    // Everybody in the network including us.
    private final Map<PeerLocation, PeerClient> _Peers = new HashMap<PeerLocation, PeerClient>();

    // Listen here, buddy. I don't want any of this socket crap.
    private final ServerSocket _ServerSocket;

    // Process new messages or block the caller on sendMessage()?
    private boolean _ProcessNewMessages = true;
    private final Object _ProcessNewMessageLock = new Object();

    // Is this server still joining the network?
    private boolean _StillJoining = false;

    // How many peers do we need to wait for NEWPEER_ACKs from?
    private Integer _NewPeerAcksLeft = null;

    // When this guy is just joining the network, any of its peers initial state is stored here.
    // This gets sent to the caller when all NEWPEER_ACKs have arrived.
    private final List<Object> _InitialPeerData = new ArrayList<Object>();

    // Who cares about us? I know I don't.
    private INetworkPeer _Owner = null;

    // Message queue.
    private final SortedMap<Integer, QueueRecord> _Queue = new TreeMap<Integer, QueueRecord>();

    private final Queue<PeerMessage> _LocalMessageQueue = new ConcurrentLinkedQueue<PeerMessage>();

    private final Runnable _LocalMessageQueueThread = new Runnable()
    {
        public void run ()
        {
            boolean forever = true;
            while (forever) {
                synchronized (_ProcessNewMessageLock) {
                    try {
                        while (!_ProcessNewMessages || _LocalMessageQueue.isEmpty())
                            _ProcessNewMessageLock.wait();

                        PeerMessage msg = _LocalMessageQueue.poll();
                        if (msg != null) {
                            sendMessage(msg, false);
                            queueIncomingMessage(msg, _Peers.size());
                        }
                    }
                    catch (Exception e) {}
                }
            }
        }
    };

    public MazewarServer (int myPort)
        throws IOException
    {
        _ServerSocket = new ServerSocket(myPort);

        Me = new PeerLocation(InetAddress.getLocalHost().getHostAddress(), myPort);
    }

    public synchronized void setPeerListener (INetworkPeer listener)
    {
        if (_Owner != null)
            throw new IllegalArgumentException("ERROR: Peer listener already specified.");
        
        _Owner = listener;
    }

    public void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException
    {
        _LocalMessageQueue.offer(new PeerClientMessage(Me, -1, data));

        _ProcessNewMessageLock.notify();
    }
    
    private synchronized void sendMessage (PeerMessage msg, boolean skipBlockCheck)
        throws IOException, ClassNotFoundException
    {
        if (msg.Timestamp != -1)
            throw new IllegalArgumentException("Timestamp on input to sendMessage() must not be specified.");

        msg.Timestamp = Clock.getNewMessageTime();

        // Send the message to everybody (including ourselves).
        for (PeerClient pc : _Peers.values())
        {
            pc.sendMessage(msg);
        }
    }

    private synchronized void queueIncomingMessage (PeerMessage msg, int acks)
        throws IOException, ClassNotFoundException
    {
        QueueRecord qr = new QueueRecord(msg, acks);

        System.out.println("Queuing message: " + qr.toString());
        
        _Queue.put(qr.Timestamp, qr);

        dispatchMessageQueue ();
    }

    private synchronized void dispatchNewPeer (NewPeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        NewPeerACK ack = new NewPeerACK(Me, -1, msg, _Owner.getCurrentState());
        int numAcks;
        if (_StillJoining)
        {
            // If this is a newly-joining client, it waits for an ACK from everyone but itself.
            numAcks = msg.PeerCount - 1;
        }
        else
        {
            // Normal clients wait for an ACK from everyone. The new guy holds back until he sees
            // the other ACKs.
            numAcks = _Peers.size();
        }
        queueIncomingMessage(ack,  numAcks);

        if (!_StillJoining)
        {
            // Send off the NEWPEER_ACK to everyone in the network right away.
            sendMessage(ack, true);
        }
    }

    private synchronized void dispatchNewPeerACK (NewPeerACK ack)
        throws IOException, ClassNotFoundException
    {
        if (_StillJoining)
        {
            // All the #peers - 1 ACKs came in. Send ours.
            ack.Timestamp = Clock.getNewMessageTime();
            for (PeerClient pc : _Peers.values())
            {
                if (!pc.Server.equals(Me))
                    pc.sendMessage(ack);
            }

            _Owner.onNetworkJoin();

            _StillJoining = false;
        }
        else
        {
            // All the #peers ACKs came in. The last one will be the new guy's ACK.
            _Owner.onNewPeerJoin(ack.InitialState);
        }

        _ProcessNewMessages = true;
        _ProcessNewMessageLock.notifyAll();
    }

    private synchronized void dispatchMessageQueue ()
        throws IOException, ClassNotFoundException
    {
        QueueRecord head = _Queue.get(_Queue.firstKey());
        while (head.isAcknowledged())
        {
            if (head.Message instanceof NewPeerMessage)
            {
                dispatchNewPeer((NewPeerMessage)head.Message);
            }
            else if (head.Message instanceof NewPeerACK)
            {
                dispatchNewPeerACK((NewPeerACK)head.Message);
            }
            else if (head.Message instanceof PeerClientMessage)
            {
                PeerClientMessage cm = (PeerClientMessage)head.Message;

                _Owner.onReceiveMessage(cm.Data);
            }
            else
            {
                throw new IllegalStateException("ERROR: Unexpected message type at queue head: " + head.Message.getClass().toString()); 
            }

            // OFF WITH ITS HEAD.
            _Queue.remove(_Queue.firstKey());

            head = _Queue.get(_Queue.firstKey());
        }
    }

    /*
        A new peer has entered the network. It only sent one person (me) the
        'HELLO'. I send everybody on the network a 'NEWPEER' message tagged with
        how many peers exist in total. This 'NEWPEER' message gets queued at each
        peer until it's at the head of the queue at which point the others send
        their NEWPEER_ACKs and things get moving.
     */
    public synchronized void processHello (PeerLocation source)
        throws IOException, ClassNotFoundException
    {
        // Connect to the new guy.
        _Peers.put(source, new PeerClient(Me, source));

        System.out.println("Processing OMGHI2U from new peer: " + source.toString());

        // Tag this message with #peers plus the new peer so the new guy knows how many ACKs to wait for.
        NewPeerMessage msg = new NewPeerMessage(source, -1, _Peers.size());

        sendMessage(msg, false);
    }

    /*
        Incoming 'NEWPEER' message from someone else on the network, sent on
        behalf of some newcomer to the network. This behaves differently based
        on who is receiving it:

            - Peers already on the network who get this must stop processing
              any new messages as soon as a 'NEWPEER' comes in. This is so they
              do not make any more broadcasts on the network until they know the
              new guy is ready.
              Upon receipt of this message, the peer sends out a NEWPEER ACK.

            - The new peer uses this (along with NewPeerMessage.PeerCount) to
              figure out how many NEWPEER ACKs to wait for from the others. It
              must also block (though it wouldn't be doing much useful anyway)
              until it gets an ACK from every peer except itself.
     */
    public synchronized void processNewPeer (NewPeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        _ProcessNewMessages = false;

        if (_NewPeerAcksLeft != null)
            throw new IllegalArgumentException("Can't process a new peer right now.");

        if (_StillJoining)
        {
            System.out.println("Incoming peer received NEW_PEER. Putting message in queue with 0 ACKs.");
        }
        else
        {
            System.out.println("Current peer received NEW_PEER. Putting in message queue with 0 ACKs.");
        }

        // Reuse the message queue to figure out when the NEWPEER_ACK should be sent.
        // When this message gets to the head of the queue, instead of sending it as
        // an event to our INetworkPeer, broadcast the ACK to everyone on the network.
        // Use '0' for the number of required ACKs here because it's only in the queue
        // so it can wait until there are no more events to be flushed.
        queueIncomingMessage(msg, 0);
    }

    public synchronized void processClientMessage (PeerClientMessage msg)
        throws IOException, ClassNotFoundException
    {
        queueIncomingMessage(msg, _Peers.size());

        sendMessage(new PeerClientACK(Me, -1, msg), false);
    }

    public synchronized void processAck (PeerACK ack)
        throws IOException, ClassNotFoundException
    {
        // What message is this trying to ACK?
        if (!_Queue.containsKey(ack.OriginalTimestamp))
            throw new IllegalArgumentException("ERROR: Client ACK for unknown timestamp.");

        _Queue.get(ack.OriginalTimestamp).acknowledge();              
    }

    public void run ()
    {
        try
        {
            boolean forever = true;
            while (true)
            {
                Thread t = new Thread(new MazewarServerRequestHandler(this, _ServerSocket.accept()));

                t.start();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

}
