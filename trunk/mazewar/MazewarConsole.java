import java.io.IOException;
import java.util.List;
import Backend.*;

public class MazewarConsole implements INetworkPeer
{
    private final INetworkServer _Server;

    public MazewarConsole(int myPort)
        throws IOException, InterruptedException
    {
        _Server = new MazewarServer (myPort, this);
    }

    public void connectToPeer (String host, int port)
        throws IOException, ClassNotFoundException
    {
        _Server.connectToPeer(host, port);
    }

    public void sendMessage (Object data)
        throws IOException, ClassNotFoundException, InterruptedException
    {
        _Server.broadcastMessage(data);

        System.out.println("Sent message: " + (String)data);
    }

    /*
        Called to retrieve any current state information which is necessary for
        a new guy who is joining the network.
     */
    public Object getCurrentState ()
    {
        return "(mritche)";
    }

    /*
        Called when the server has finished joining the network. This happens
        when all NEWPEER_ACK messages have been received.

        initialPeerData is a list of Objects corresponding to any state the
        other peers may have given us in the handshaking process. If the caller
        has to do anything with this data to come up with its own state, it
        does so and returns its corresponding initial state here. A null return
        value indicates no initial state.
     */
    public Object processInitialPeerState (List<Object> initialPeerData)
    {
        System.out.println("CONSOLE EVENT: RECEIVED INITIAL PEER STATE");
        for (Object o : initialPeerData)
        {
            System.out.println("\t " + (String)o);
        }

        return "(mritche's location)";
    }

    /*
        Called when new messages will start/stop to be queued without processing.
     */
    public void onStopMessageProcessing ()
    {
        System.out.println("CONSOLE EVENT: MESSAGE PROCESSING STOPPED.");
    }

    public void onStartMessageProcessing ()
    {
        System.out.println("CONSOLE EVENT: MESSAGE PROCESSING STARTED.");
    }

    /*
        Called when a peer (not us) has successfully joined the network.
     */
    public void onNewPeerJoin (Object newPeerData)
    {
        System.out.println("CONSOLE EVENT: NEW PEER JOINED (" + (String)newPeerData + ")");
    }

    /*
        Called when we've successfully joined the network.
     */
    public void onNetworkJoin ()
    {
        System.out.println("CONSOLE EVENT: SUCCESSFUL NETWORK JOIN");
    }

    /*
        Deliver a message to the application. Huzzah, we finally made it!
     */
    public void onReceiveMessage (Object msg)
    {
        System.out.println("INCOMING MESSAGE: " + (String)msg);
    }

    public static void main(String args[])
    {
        //
        // Arguments:
        //  java MazewarConsole <myName> <myPort> [<peerName> <peerPort>]
        //
        try
        {
        	String myName = args[0];
        	int myPort = Integer.parseInt(args[1]);

            INetworkPeer self = new MazewarConsole(myPort);

            if (args.length == 4)
        	{
        		String peerHost = args[2];
        		int peerPort = Integer.parseInt(args[3]);

                self.connectToPeer(peerHost, peerPort);
            }

            self.sendMessage("Hello from " + myName);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
