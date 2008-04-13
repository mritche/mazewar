import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class MazewarServer implements Runnable, INetworkServer, ITOMRecipient
{
    public final PeerLocation Me;

    // L-L-L-L-Lamport!
    public final MazewarClock Clock;

    // Everybody in the network including us.
    private final Map<PeerLocation, PeerClient> _Peers = new HashMap<PeerLocation, PeerClient>();

    // Listen here, buddy. I don't want any of this socket crap.
    private final ServerSocket _ServerSocket;

    // Process new messages or block the caller on sendMessage()?
    private boolean _ProcessNewMessages = false;
    private final Object _ProcessNewMessageLock = new Object();

    // Is this server still joining the network?
    private boolean _StillJoining = false;
    private final Semaphore _InitializingLock = new Semaphore(2);

    // When this guy is just joining the network, any of its peers initial state is stored here.
    // This gets sent to the caller when all NEWPEER_ACKs have arrived.
    private final List<Object> _InitialPeerData = new ArrayList<Object>();

    // Who cares about us? I know I don't.
    private final INetworkPeer _Owner;

    private final ITotalOrderQueue _TOMQueue;

    // All incoming messages on the local host come in here first and are then
    // multicast to other peers / queued in the global message queue until they can be delivered.
    // This is so we can stop processing new messages when a new guy enters the network
    // (but not block the caller entirely).
    private final Queue<PeerClientMessage> _LocalMessageQueue = new ConcurrentLinkedQueue<PeerClientMessage>();
    private final Runnable _LocalMessageQueueThread = new Runnable()
    {
        public void run ()
        {
            Log.Write("Local message queue background thread started.");
            _InitializingLock.release();

            boolean forever = true;
            while (forever) {
                synchronized (_ProcessNewMessageLock) {
                    try {
                        while (!_ProcessNewMessages)
                            _ProcessNewMessageLock.wait();

                        PeerClientMessage msg = _LocalMessageQueue.poll();
                        if (msg != null) {
                            sendMessage(msg);
                        }
                    }
                    catch (Exception e) {}
                }
            }
        }
    };

    public MazewarServer (int myPort, INetworkPeer listener)
        throws IOException, InterruptedException
    {
        _ServerSocket = new ServerSocket(myPort);
        _Owner = listener;

        Me = new PeerLocation(InetAddress.getLocalHost().getHostAddress(), myPort);

        Log.Write("Starting MazewarServer @ " + Me.toString());

        Clock = new MazewarClock(Me);

        Log.Write("Current clock value: " + Clock.getTime());
        
        _TOMQueue = new MazewarTotalOrderQueue(this);

        (new Thread(this)).start();

        startAcceptingNewMessages();

        _InitializingLock.acquire();

        _Peers.put(Me, new PeerClient(Me, Me));        
    }

    public void connectToPeer (String host, int port)
        throws IOException, ClassNotFoundException
    {
        if (!_Peers.isEmpty())
            throw new IllegalArgumentException("ERROR: Already connected to existing network.");

        PeerLocation peer = new PeerLocation(host, port);
        
        PeerClient pc = new PeerClient(Me, peer);

        _Peers.put(peer, pc);

        pc.sendMessage(new PeerMessage(Me, Clock.getNewMessageTime(), PeerMessageID.HELLO));
    }

    public void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        if (!_LocalMessageQueue.offer(new PeerClientMessage(Me, null, data)))
            throw new IllegalStateException("Unable to add PeerClientMessage to local queue.");

        Log.Write("Local message queue has " + _LocalMessageQueue.size() + " items waiting.");

        synchronized (_ProcessNewMessageLock)
        {
            if (_ProcessNewMessages)
                _ProcessNewMessageLock.notify();            
        }
    }
    
    private void sendMessage (PeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        if (msg.Timestamp != null)
            throw new IllegalArgumentException("Timestamp on input to sendMessage() must not be specified.");

        msg.Timestamp = Clock.getNewMessageTime();

        // Send the message to everybody (including ourselves).
        for (PeerClient pc : _Peers.values())
        {
            pc.sendMessage(msg);
        }
    }

    private void stopAcceptingNewMessages ()
    {
        if (_Owner != null)
            _Owner.onStopMessageProcessing();
        
        synchronized (_ProcessNewMessageLock)
        {
            _ProcessNewMessages = false;
            _ProcessNewMessageLock.notifyAll();
        }
    }

    private void startAcceptingNewMessages ()
    {
        if (_Owner != null)
            _Owner.onStartMessageProcessing();

        synchronized (_ProcessNewMessageLock)
        {
            _ProcessNewMessages = true;
            _ProcessNewMessageLock.notifyAll();
        }
    }


    private synchronized void dispatchNewPeer (NewPeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        NewPeerACK ack = new NewPeerACK(Me, null, msg, _Owner.getCurrentState());
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
        _TOMQueue.queueIncomingMessage(ack,  numAcks);

        if (!_StillJoining)
        {
            // Send off the NEWPEER_ACK to everyone in the network right away.
            sendMessage(ack);
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

        startAcceptingNewMessages();
    }

    public synchronized void dispatchMessage (PeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        if (msg instanceof NewPeerMessage)
        {
            dispatchNewPeer((NewPeerMessage)msg);
        }
        else if (msg instanceof NewPeerACK)
        {
            dispatchNewPeerACK((NewPeerACK)msg);
        }
        else if (msg instanceof PeerClientMessage)
        {
            PeerClientMessage cm = (PeerClientMessage)msg;

            _Owner.onReceiveMessage(cm.Data);
        }
        else
        {
            throw new IllegalStateException("ERROR: Unexpected message type at queue head: " + msg.getClass().toString());
        }
    }

    /*
        A new peer has entered the network. It only sent one person (me) the
        'HELLO'. I send everybody on the network a 'NEWPEER' message tagged with
        how many peers exist in total. This 'NEWPEER' message gets queued at each
        peer until it's at the head of the queue at which point the others send
        their NEWPEER_ACKs and things get moving.
     */
    public void processHello (PeerLocation source)
        throws IOException, ClassNotFoundException
    {
        // Connect to the new guy.
        _Peers.put(source, new PeerClient(Me, source));

        Log.Write("Processing OMGHI2U from new peer: " + source.toString());

        // Tag this message with #peers (incl. the new peer) so the new guy knows how many ACKs to wait for.
        sendMessage(new NewPeerMessage(source, null, _Peers.size()));
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
    public void processNewPeer (NewPeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        stopAcceptingNewMessages();

        if (_StillJoining)
        {
            Log.Write("Incoming peer received NEW_PEER. Putting message in queue with 0 ACKs.");
        }
        else
        {
            Log.Write("Current peer received NEW_PEER. Putting in message queue with 0 ACKs.");
        }

        // Reuse the message queue to figure out when the NEWPEER_ACK should be sent.
        // When this message gets to the head of the queue, instead of sending it as
        // an event to our INetworkPeer, broadcast the ACK to everyone on the network.
        // Use '0' for the number of required ACKs here because it's only in the queue
        // so it can wait until there are no more events to be flushed.
        
        // TODO: Do I forward this entirely, or update the timestamp?
        _TOMQueue.queueIncomingMessage(msg, 0);
    }

    public void processClientMessage (PeerClientMessage msg)
        throws IOException, ClassNotFoundException
    {
        _TOMQueue.queueIncomingMessage(msg, _Peers.size());

        sendMessage(new PeerClientACK(Me, null, msg));
    }

    public void processAck (PeerACK ack)
        throws IOException, ClassNotFoundException
    {
        _TOMQueue.ackIncomingMessage(ack);              
    }

    public void run ()
    {
        try
        {
            Log.Write("MazewarServer background thread started.");

             _InitializingLock.release();
            
            Thread localQueueThread = new Thread(_LocalMessageQueueThread);
            localQueueThread.start();

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
