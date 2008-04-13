import java.io.IOException;

public class MazewarConsole
{
    private MazewarServer _Server;

    public MazewarConsole(String myName, int myPort)
        throws IOException
    {
        _Server = new MazewarServer (myPort);
    }
	
	public void initAlone ()
	{
		// Start listening for incoming connections on myPort.
        (new Thread(_Server)).start();
    }
	
	public void initFromPeer (String peerHost, int peerPort)
	{
        // Set ourselves
        initAlone();
        
        // Connect to the peer, get a list of every peer in the system.
		
		// Connect to every peer, get status updates about them.
		
		// Join the game.
	}

    public static void main(String args[])
    {
        try
        {
        	String myName = args[0];
        	int myPort = Integer.parseInt(args[1]);
        	
        	if (args.length == 4)
        	{
        		String peerHost = args[2];
        		int peerPort = Integer.parseInt(args[3]);
        	}
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
