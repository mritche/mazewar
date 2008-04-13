import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;


public class MulticastQueue
{
	private class MulticastServer implements Runnable
	{
		private ServerSocket s;
		public MulticastServer(int listenPort) throws IOException
		{
			s = new ServerSocket(listenPort);
			Thread t = new Thread(this);
			t.start();
		}
		
		public void run() {
			try {
				System.out.println("Begin accepting");
				while(true)
				{
					Socket socket = s.accept();
					System.out.println("Client connected");
					Thread t = new Thread(new MulticastConnection(socket));
					t.start();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	private class MulticastConnection implements Runnable
	{
		private Socket s;
		public ObjectOutputStream out;
		public ObjectInputStream in;
		private boolean needToHandshake = false;
		public MulticastConnection(InetSocketAddress remoteAddress) throws UnknownHostException, IOException
		{
			s = new Socket(remoteAddress.getAddress(), remoteAddress.getPort());
			System.out.printf("Connected to %s\n", remoteAddress);
			out = new ObjectOutputStream(s.getOutputStream());
			in = new ObjectInputStream(s.getInputStream());
			out.writeObject(ownAddress);
			System.out.println("Sent own address");
		}
		public MulticastConnection(Socket s) throws IOException {
			this.s = s;
			in = new ObjectInputStream(s.getInputStream());
			out = new ObjectOutputStream(s.getOutputStream());
			needToHandshake = true;
		}
		@Override
		public void run() {
			try {
				if(needToHandshake)
				{
					InetSocketAddress remoteAddress = (InetSocketAddress)in.readObject();
					addNewClient(this, remoteAddress);
				}
				System.out.println("Connection established");
				attemptStart();
				while(true)
				{
					Object message = in.readObject();
					if(message instanceof ArrayList)
					{
						ArrayList<InetSocketAddress> newClients = (ArrayList<InetSocketAddress>)message;
						System.out.printf("Client List: %s\n", newClients);
						connectToNewClients(newClients);
					}
					else if(message instanceof NetworkMessage)
					{
						deliverMessage((NetworkMessage)message);
					}
					else
					{
						System.out.println("Received unknown message");
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
	}
	
	private InetSocketAddress ownAddress;
	private Map<MulticastConnection, InetSocketAddress> clientList = new HashMap<MulticastConnection, InetSocketAddress>();
	private int expectedConnections;
	private MultiplayerMazeController controller;
	
	public MulticastQueue(int listenPort, int numPlayers, String joinHost,
			int joinPort, Client client) throws IOException, InterruptedException {
		ownAddress = new InetSocketAddress(InetAddress.getLocalHost(), listenPort);
		expectedConnections = numPlayers - 1;
		MulticastServer server = new MulticastServer(listenPort);
		if(joinHost != null)
		{
			connect(joinHost, joinPort);
		}
		
	}
	
	public void setController(MultiplayerMazeController controller)
	{
		this.controller = controller;
	}

	public void deliverMessage(NetworkMessage message) {
		if(controller == null) return;
		if(message.orientation != null)
			message.orientation = Direction.convert(message.orientation);
		if(message.event != null)
			message.event = ClientEvent.convert(message.event);
		controller.deliverMessage(message);
		
	}

	public synchronized void connectToNewClients(ArrayList<InetSocketAddress> newClients) throws UnknownHostException, IOException {
		for(InetSocketAddress address : newClients)
		{
			if(clientList.values().contains(address)) continue;
			
			connect(address.getHostName(), address.getPort());
		}
		
	}

	public synchronized void addNewClient(MulticastConnection con,
			InetSocketAddress remoteAddress) throws IOException {
		System.out.printf("Received new client %s\n", remoteAddress);
		sendClientList(con);
		clientList.put(con, remoteAddress);
		
	}

	private synchronized void sendClientList(MulticastConnection con) throws IOException {
		ArrayList<InetSocketAddress> clients = new ArrayList<InetSocketAddress>(clientList.values());
		con.out.writeObject(clients);	
		System.out.println("Sent client list");
	}

	private synchronized void connect(String host, int port) throws UnknownHostException, IOException {
		InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
		MulticastConnection con = new MulticastConnection(remoteAddress);
		clientList.put(con, remoteAddress);
		Thread t = new Thread(con);
		t.start();
	}

	public synchronized void attemptStart()
	{
		if(expectedConnections == clientList.size())
			this.notify();
	}
	public synchronized void startMulticast() throws InterruptedException {
		this.wait();
		
	}

	public void broadcast(NetworkMessage message) throws IOException {
		for(MulticastConnection con : clientList.keySet())
		{
			con.out.writeObject(message);
		}
	}	

}
