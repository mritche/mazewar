package Backend;

import java.io.*;
import java.net.*;

public class PeerClient
{
    public final PeerLocation Me;
    public final PeerLocation Server;

    private Socket _ClientSocket;
    private ObjectOutputStream _oosClientRequest;
	private ObjectInputStream _oisServerResponse;

    public void connect ()
        throws IOException
    {
        _ClientSocket = new Socket(Server.Host, Server.Port);
        _oosClientRequest = new ObjectOutputStream(_ClientSocket.getOutputStream());
        _oisServerResponse = new ObjectInputStream(_ClientSocket.getInputStream());
    }

    PeerClient (PeerLocation me, PeerLocation server)
        throws IOException
    {
        Me = me;
        Server = server;

        connect();
    }

    public boolean isConnected ()
	{
		return _ClientSocket != null && _ClientSocket.isConnected();
	}

	public synchronized void sendMessage (PeerMessage msg)
		throws IOException, ClassNotFoundException
	{
        Log.Write("SEND[ " + Server.toString() + ", " + msg.toString() + " ]");

        _oosClientRequest.writeObject(msg);

        Object o = _oisServerResponse.readObject();
        if (!(o instanceof PeerMessage) || ( ((PeerMessage)o).MessageID != PeerMessageID.EMPTY ) )
        {
            Log.Write("Server sent invalid response: " + o.toString());
        }
    }

    public void reconnect ()
        throws IOException, ClassNotFoundException
    {
		if (this.isConnected())
		{
			this.disconnect();
		}

		connect();
	}

	public void disconnect ()
        throws IOException, ClassNotFoundException
    {
        if (isConnected())
        {
            //sendMessage(new PeerMessage(Me, null, PeerMessageID.GOODBYE));
        }

        if (_oosClientRequest != null)
        {
            _oosClientRequest.close();
            _oosClientRequest = null;
        }

        if (_oisServerResponse != null)
        {
            _oisServerResponse.close();
            _oisServerResponse = null;
        }

        if (_ClientSocket != null)
        {
            _ClientSocket.close();
            _ClientSocket = null;
        }
	}
}
