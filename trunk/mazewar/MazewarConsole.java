import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import Backend.*;

public class MazewarConsole implements INetworkPeer
{
    private final String _Name;
    private INetworkServer _Server = null;

    public MazewarConsole (String name)
    {
        _Name = name;
    }

    public void attachToServer (INetworkServer server)
        throws IOException, ClassNotFoundException
    {
        if (_Server != null)
            throw new IllegalArgumentException("ERROR: Server already attached to console.");

        _Server = server;
    }
    /*
        Called to retrieve any current state information which is necessary for
        a new guy who is joining the network.
     */
    public Object getCurrentState ()
    {
        return "(_Name = " + _Name + ")";
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
        System.out.println("Received initial peer state:");
        for (Object o : initialPeerData)
        {
            System.out.println("\t " + (String)o);
        }

        System.out.print("> ");

        return "(_Name = " + _Name + ", _Location = {null})";
    }

    /*
        Called when new messages will start/stop to be queued without processing.
     */
    public void onStopMessageProcessing ()
    {
    }

    public void onStartMessageProcessing ()
    {
    }

    /*
        Called when a peer (not us) has successfully joined the network.
     */
    public void onNewPeerJoin (Object newPeerData)
    {
        System.out.println("New peer joined: " + (String)newPeerData);
        System.out.print("> ");
    }

    /*
        Called when we've successfully joined the network.
     */
    public void onNetworkJoin ()
    {
        System.out.println("Successfully joined the network.");

        try
        {
			BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
			String line;
			System.out.print("> ");
			while ((line = stdin.readLine()) != null)
			{
				System.out.println("Sent message: " + line);
                _Server.broadcastMessage(line);
                System.out.print("> ");
			}
		}
		catch (IOException e) {}
        catch (ClassNotFoundException e) {}
        catch (InterruptedException e) {}


        try
        {
            boolean forever = true;
            Integer i = (int)(Math.random() * 10000);
            while (forever)
            {
                System.out.println("Sent message: " + i.toString());
                _Server.broadcastMessage(Integer.toString(i));
                i++;
                Thread.sleep(1500);
            }
        }
        catch (Exception e)
        {
            synchronized (Log.class)
            {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    /*
        Deliver a message to the application. Huzzah, we finally made it!
     */
    public void onReceiveMessage (Object msg)
    {
        System.out.println("Received message: " + (String)msg);
        System.out.print("> ");
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

            // Local client.
            MazewarConsole console = new MazewarConsole(myName);

            // Local server. Attach it to the console.
            INetworkServer server;

            if (args.length == 4)
        	{
        		String peerHost = args[2];
        		int peerPort = Integer.parseInt(args[3]);

                server = new MazewarServer(myPort, console, peerHost, peerPort);
            }
            else
            {
                server = new MazewarServer(myPort, console);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
