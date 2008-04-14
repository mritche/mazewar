package Backend;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.*;

public class MazewarServer implements Runnable, INetworkServer, ITOMRecipient
{
    public final PeerLocation Me;

    // L-L-L-L-Lamport!
    public final MazewarClock Clock;

    // Everybody in the network including us.
    private final SortedMap<PeerLocation, PeerClient> _Peers = new TreeMap<PeerLocation, PeerClient>();

    // Listen here, buddy. I don't want any of this socket crap.
    private final ServerSocket _ServerSocket;

    // Is this server still joining the network?
    private boolean _StillJoining = false;
    private final Semaphore _InitializingLock = new Semaphore(3);

    private final Lock _LocalQueueLock = new ReentrantLock();
    private final Condition _LocalQueueNotEmpty = _LocalQueueLock.newCondition();
    private final Condition _LocalQueueProcessMessages = _LocalQueueLock.newCondition();
    private final Condition _LocalIncomingQueueNotEmpty = _LocalQueueLock.newCondition();
    private boolean _ProcessNewMessages = false;

    // When this guy is just joining the network, any of its peers initial state is stored here.
    // This gets sent to the caller when all NEWPEER_ACKs have arrived.
    private final SortedMap<PeerLocation,Object> _InitialPeerData = new TreeMap<PeerLocation,Object>();
    private PeerLocation _IncomingPeer = null;

    // Who cares about us? I know I don't.
    private final INetworkPeer _Owner;

    private final ITotalOrderQueue _TOMQueue;

    // All incoming messages on the local host come in here first and are then
    // multicast to other peers / queued in the global message queue until they can be delivered.
    // This is so we can stop processing new messages when a new guy enters the network
    // (but not block the caller entirely).
    private final Queue<PeerMessage> _LocalMessageQueue = new LinkedList<PeerMessage>();
    private final Queue<PeerMessage> _LocalIncomingMessageQueue = new LinkedList<PeerMessage>();
    private final Runnable _LocalMessageQueueThread = new Runnable()
    {
        public void run ()
        {
            Log.Write("Local message queue background thread started.");
            _InitializingLock.release();

            boolean forever = true;
            while (forever) {
                    try {
                        _LocalQueueLock.lock();

                        while (!_ProcessNewMessages)
                            _LocalQueueProcessMessages.await();

                        while (_LocalMessageQueue.isEmpty() || !_ProcessNewMessages)
                            _LocalQueueNotEmpty.await();

                        PeerMessage msg = _LocalMessageQueue.poll();
                        if (msg != null) {
                            sendMessage(msg);
                        }
                        else
                        {
                            System.out.println("Received null message.");
                        }

                        _LocalQueueLock.unlock();
                    }
                    catch (Exception e) {}
            }
        }
    };

    private final Runnable _LocalIncomingQueueThread = new Runnable()
    {
        public void run ()
        {
            Log.Write("Local message dispatch queue background thread started.");
            _InitializingLock.release();

            boolean forever = true;
            while (forever) {
                    try {
                        _LocalQueueLock.lock();

                        while (!_ProcessNewMessages)
                            _LocalQueueProcessMessages.await();

                        while (_LocalIncomingMessageQueue.isEmpty() || !_ProcessNewMessages)
                            _LocalIncomingQueueNotEmpty.await();

                        while (!_LocalIncomingMessageQueue.isEmpty() && _ProcessNewMessages)
                        {
                            PeerMessage msg = _LocalIncomingMessageQueue.poll();
                            if (msg != null) {
                                Log.Write("RECV[ " + msg.toString() + " ]");
                                if (msg instanceof PeerClientMessage)
                                {
                                    // L-L-L-L-Lamport!
                                    Clock.syncMessageTime(msg.Timestamp);

                                    processClientMessage((PeerClientMessage)msg);
                                }
                                else if (msg instanceof PeerClientACK)
                                {
                                    // L-L-L-L-Lamport!
                                    Clock.syncMessageTime(msg.Timestamp);

                                    processClientAck((PeerClientACK)msg);
                                }
                                else
                                {
                                    System.out.println("Unexpected incoming queued message.");
                                }
                            }
                            else
                            {
                                System.out.println("Received null message.");
                            }
                        }

                        _LocalQueueLock.unlock();
                    }
                    catch (Exception e) {}
            }
        }
    };
    
    public MazewarServer (String myHost, int myPort, INetworkPeer listener)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        _ServerSocket = new ServerSocket(myPort);
        _Owner = listener;

        Me = new PeerLocation(myHost, myPort);

        Log.Write("Starting MazewarServer @ " + Me.toString());

        Clock = new MazewarClock(Me);

        Log.Write("Current clock value: " + Clock.getTime());
        
        _TOMQueue = new MazewarTotalOrderQueue(this);

        (new Thread(this)).start();

        startAcceptingNewMessages();

        _InitializingLock.acquire();

        _Peers.put(Me, new PeerClient(Me, Me));

        listener.attachToServer(this);

        _Owner.onNetworkJoin();
    }

    public MazewarServer (String myHost, int myPort, INetworkPeer listener, String peerHost, int peerPort)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        _ServerSocket = new ServerSocket(myPort);
        _Owner = listener;

        Me = new PeerLocation(myHost, myPort);

        Log.Write("Starting MazewarServer @ " + Me.toString());

        Clock = new MazewarClock(Me);

        Log.Write("Current clock value: " + Clock.getTime());

        _TOMQueue = new MazewarTotalOrderQueue(this);

        (new Thread(this)).start();

        startAcceptingNewMessages();

        _InitializingLock.acquire();

        _Peers.put(Me, new PeerClient(Me, Me));

        listener.attachToServer(this);
        
        connectToPeer(peerHost, peerPort);
    }

    private synchronized void connectToPeer (String host, int port)
        throws IOException, ClassNotFoundException
    {
        if (_Peers.size() > 1)
            throw new IllegalArgumentException("ERROR: Already connected to existing network.");

        stopAcceptingNewMessages();
        
        _StillJoining = true;
        
        PeerLocation peer = new PeerLocation(host, port);
        
        PeerClient pc = new PeerClient(Me, peer);

        _Peers.put(peer, pc);

        pc.sendMessage(new PeerMessage(Me, Clock.getNewMessageTime(), PeerMessageID.HELLO));
    }

    public void broadcastMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        queueMessageForBroadcast (new PeerClientMessage(Me, data));
    }

    private void queueMessageForBroadcast (PeerMessage msg)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        if (!_StillJoining)
        {
            _LocalQueueLock.lock();

            if (!_LocalMessageQueue.offer(msg))
                throw new IllegalStateException("Unable to add PeerMessage to local queue.");

            _LocalQueueNotEmpty.signal();

            if (!_ProcessNewMessages)
                Log.Write("Local message queue has " + _LocalMessageQueue.size() + " items waiting.");

            _LocalQueueLock.unlock();
            
        }
        else
        {
            sendMessage(msg);
        }
    }
    
    private void sendMessage (PeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        // Hackhackhack
        if (!msg.Timestamp.equals(GlobalTimestamp.Undefined) && !(msg instanceof NewPeerACK))
            throw new IllegalArgumentException("Timestamp on input to sendMessage() must be undefined.");
        else if (msg.Timestamp.equals(GlobalTimestamp.Undefined))
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

        _LocalQueueLock.lock();
        _ProcessNewMessages = false;
        _LocalQueueProcessMessages.signal();
        _LocalQueueLock.unlock();
    }

    private void startAcceptingNewMessages ()
    {
        if (_Owner != null)
            _Owner.onStartMessageProcessing();

        _LocalQueueLock.lock();
        _ProcessNewMessages = true;
        _LocalQueueProcessMessages.signal();
        _LocalQueueLock.unlock();
    }


    private void dispatchNewPeer (NewPeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        // Don't ask for the current state from the new guy.
        Object myCurrentState;
        if (!_StillJoining)
            myCurrentState = _Owner.getCurrentState();
        else
            myCurrentState = null;

        NewPeerACK ack = new NewPeerACK(Me, msg.Timestamp, msg, myCurrentState);
        int numAcks;
        if (_StillJoining)
        {
            // If this is a newly-joining client, it waits for an ACK from everyone but itself.
            numAcks = msg.PeerCount - 1;
        }
        else
        {
            if (!_Peers.containsKey(msg.Source))
                _Peers.put(msg.Source, new PeerClient(Me, msg.Source));

            // Normal clients wait for an ACK from everyone.
            numAcks = _Peers.size();

            if (numAcks != msg.PeerCount)
            {
                Log.Write("WARNING: numAcks = " + Integer.toString(numAcks) +  " but msg.PeerCount = " + msg.PeerCount.toString());
            }
        }
        _TOMQueue.queueIncomingMessage(ack, numAcks);

        if (!_StillJoining)
        {
            // Send off the NEWPEER_ACK to everyone in the network right away.
            sendMessage(ack);
        }
    }

    private void dispatchNewPeerACK (NewPeerACK ack)
        throws IOException, ClassNotFoundException
    {
        if (_StillJoining)
        {
            //synchronized (this)
            {
                ack.InitialState = _Owner.processInitialPeerState(new ArrayList<Object>(_InitialPeerData.values()));

                // All the #peers - 1 ACKs came in. Send ours.
                ack.Timestamp = Clock.getNewMessageTime();
                for (PeerClient pc : _Peers.values())
                {
                    Log.Write("Peer: " + pc.Server.toString());

                    if (!pc.Server.equals(Me))
                        pc.sendMessage(ack);
                }

                _StillJoining = false;
                _IncomingPeer = null;
                startAcceptingNewMessages();
            }
            
            _Owner.onNetworkJoin();
        }
        else
        {
            Object initialData = _InitialPeerData.get(_IncomingPeer);

            _IncomingPeer = null;
            startAcceptingNewMessages();

            // All the #peers ACKs came in.
            _Owner.onNewPeerJoin(initialData);
        }
    }

    public void queueClientMessageForDispatch (PeerMessage msg)
        throws IOException, ClassNotFoundException
    {
        _LocalQueueLock.lock();

        if (!_LocalIncomingMessageQueue.offer(msg))
            throw new IllegalStateException("Unable to add PeerMessage to local dispatch queue.");

        _LocalIncomingQueueNotEmpty.signal();

        if (!_ProcessNewMessages)
            Log.Write("Local dispatch message queue has " + _LocalIncomingMessageQueue.size() + " items waiting.");

        _LocalQueueLock.unlock();
    }


    public void dispatchMessage (PeerMessage msg)
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
        throws IOException, ClassNotFoundException, InterruptedException
    {
        // Connect to the new guy.
        _Peers.put(source, new PeerClient(Me, source));

        _InitialPeerData.clear();

        Log.Write("Processing OMGHI2U from new peer: " + source.toString());

        // Tag this message with #peers (incl. the new peer) so the new guy knows how many ACKs to wait for.
        sendMessage(new NewPeerMessage(source, _Peers.size()));
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

        _IncomingPeer = msg.Source;

        // Reuse the message queue to figure out when the NEWPEER_ACK should be sent.
        // When this message gets to the head of the queue, instead of sending it as
        // an event to our INetworkPeer, broadcast the ACK to everyone on the network.
        // Use '0' for the number of required ACKs here because it's only in the queue
        // so it can wait until there are no more events to be flushed.
        //msg.Timestamp = Clock.getNewMessageTime();
        
        _TOMQueue.queueIncomingMessage(msg, 0);
    }

    public void processClientMessage (PeerClientMessage msg)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        _TOMQueue.queueIncomingMessage(msg, _Peers.size());

        queueMessageForBroadcast(new PeerClientACK(Me, msg));
    }

    public void processClientAck (PeerClientACK ack)
        throws IOException, ClassNotFoundException
    {
        _TOMQueue.ackIncomingMessage(ack);              
    }

    public void processNewPeerAck (NewPeerACK ack)
        throws IOException, ClassNotFoundException
    {
        if (!_Peers.containsKey(ack.Source))
        {
            _Peers.put(ack.Source, new PeerClient(Me, ack.Source));
        }

        Log.Write("Putting InitialPeerState (" + ack.Source.toString() + "): " + ack.InitialState.toString());

        _InitialPeerData.put(ack.Source, ack.InitialState);

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

            Thread localDispatchQueueThread = new Thread(_LocalIncomingQueueThread);
            localDispatchQueueThread.start();

            boolean forever = true;
            while (true)
            {
                Thread t = new Thread(new MazewarClientConnectionHandler(this, _ServerSocket.accept()));

                Log.Write("Started thread: " + t.getName());

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
