import java.util.List;
import java.io.IOException;

public interface INetworkPeer
{
    public abstract void connectToPeer (String host, int port)
        throws IOException, ClassNotFoundException;

    /*
        Called to retrieve any current state information which is necessary for
        a new guy who is joining the network.
     */
    public abstract Object getCurrentState ();

    /*
        Called when the server has finished joining the network. This happens
        when all NEWPEER_ACK messages have been received.

        initialPeerData is a list of Objects corresponding to any state the
        other peers may have given us in the handshaking process. If the caller
        has to do anything with this data to come up with its own state, it
        does so and returns its corresponding initial state here. A null return
        value indicates no initial state.
     */
    public abstract Object processInitialPeerState (List<Object> initialPeerData);

    /*
        Called when new messages will start/stop to be queued without processing.
     */
    public abstract void onStopMessageProcessing ();
    public abstract void onStartMessageProcessing ();

    /*
        Called when a peer (not us) has successfully joined the network.
     */
    public abstract void onNewPeerJoin (Object newPeerData);

    /*
        Called when we've successfully joined the network.
     */
    public abstract void onNetworkJoin ();

    /*
        Deliver a message to the application. Huzzah, we finally made it!
     */
    public abstract void onReceiveMessage (Object msg);

    public abstract void sendMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException;

}
