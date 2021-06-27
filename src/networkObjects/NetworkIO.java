package networkObjects;

import processing.core.*;
import processing.net.*;
import java.util.*;
import java.net.URL;
import java.io.*;

/**
 * A class that manages all network connections and I/O.
 * 
 * @author Yehoshua Halle
 *
 */

public class NetworkIO {
	private final static int DEFAULTBUFFERSIZE = 8192;
	// Eight bytes with many 1s in a row. I understand that it adds a lot of data,
	// but this has good results for splitting diverse packet data.
	// If you need to send a long value of -2, you should change this for both the
	// sending and recieving end.
	private final byte[] ENDSEQUENCE = ByteOps.toByteArray((long) -2);
	private PApplet parent;
	private HashMap<String, Packet> packetTypes;
	// No action for receiving since it may interrupt loading packets into queues.
	private NetAction serverConnectAction, serverDisconnectAction, clientDisconnectAction;
	// Internal server
	private String publicIP;
	private int serverPort;
	private Server server;
	// Buffer size for individual clients connected to the internal server.
	int serverBufferSize;
	private HashMap<String, Client> serverClients;
	private HashMap<String, ByteQueue> serverBuffers;
	private HashMap<String, LinkedList<Packet>> serverData;
	// External connections
	// Buffer size for clients connecting to external servers.
	int clientBufferSize;
	private HashMap<String, Client> clients;
	private HashMap<String, ByteQueue> clientBuffers;
	private HashMap<String, LinkedList<Packet>> clientData;

	/**
	 * Constructs a new NetworkIO object with a custom buffer size.
	 */
	public NetworkIO(PApplet parent, int serverPort, int serverBufferSizeInBytes, int clientBufferSizeInBytes,
			boolean findPublicIP) {
		this.parent = parent;
		this.serverPort = serverPort;
		packetTypes = new HashMap<String, Packet>();
		serverConnectAction = null;
		serverDisconnectAction = null;
		clientDisconnectAction = null;
		server = null;
		serverBufferSize = serverBufferSizeInBytes;
		serverClients = new HashMap<String, Client>();
		serverBuffers = new HashMap<String, ByteQueue>();
		serverData = new HashMap<String, LinkedList<Packet>>();
		clientBufferSize = clientBufferSizeInBytes;
		clients = new HashMap<String, Client>();
		clientBuffers = new HashMap<String, ByteQueue>();
		clientData = new HashMap<String, LinkedList<Packet>>();
		if (findPublicIP) {
			findPublicIP();
		}
	}

	/**
	 * Constructs a new NetworkIO object with all buffer sizes being initialized to
	 * the one provided here.
	 */
	public NetworkIO(PApplet parent, int serverPort, int bufferSizeInBytes) {
		this(parent, serverPort, bufferSizeInBytes, bufferSizeInBytes, false);
	}

	public NetworkIO(PApplet parent, int serverPort, int bufferSizeInBytes, boolean findPublicIP) {
		this(parent, serverPort, bufferSizeInBytes, bufferSizeInBytes, findPublicIP);
	}

	/**
	 * Constructs a new NetworkIO object with the default buffer size of 8192 (a bit
	 * over 8mb).
	 */
	public NetworkIO(PApplet parent, int serverPort) {
		// initializes with 8mb of buffer data for every client
		this(parent, serverPort, DEFAULTBUFFERSIZE, DEFAULTBUFFERSIZE, false);
	}

	public NetworkIO(PApplet parent, int serverPort, boolean findPublicIP) {
		this(parent, serverPort, DEFAULTBUFFERSIZE, DEFAULTBUFFERSIZE, findPublicIP);
	}

	public String getPublicIP() {
		return publicIP;
	}

	/**
	 * If you want to use the built-in server to its full capacity, put this in the
	 * serverEvent() method like this: public void serverEvent(Server s, Client c) {
	 * NetIO.putInServerEvent(s, c); } This allows the server keeps track of
	 * connected clients.
	 */
	public void putInServerEvent(Server ref, Client newClient) {
		if (this.server == null || ref != this.server)
			return;
		addClient(newClient);
		if (serverConnectAction != null) {
			serverConnectAction.act(this, newClient);
		}
	}

	/**
	 * If you want to use the built-in server and clients to their full capacity,
	 * put this in the clientEvent() method like this: public void
	 * clientEvent(Client c) { NetIO.putInClientEvent(c); }
	 */
	public void putInClientEvent(Client c) {
		// If the client is both an external connection and a client connected to the
		// server its data cannot be read twice.
		// However, the NetworkIO Object checks for this case and directly transfers the
		// data to the corresponding buffer.
		// So, the case when the client is "circular" shouldn't happen.
		if (hasConnection(c)) { // for external connections
			readFromConnection(c);
			return;
		}
		if (isServerClient(c)) { // for internal clients
			readFromServerClient(c);
			return;
		}
	}

	/**
	 * If you want to use the built-in server to its full capacity, put this in the
	 * disconnectEvent() method like this: public void disconnectEvent(Client c) {
	 * NetIO.putInDisconnectEvent(c); } You need to do this to prevent data from
	 * being sent to disconnected clients.
	 */
	public void putInDisconnectEvent(Client oldClient) {
		if (serverClients.containsKey(oldClient.ip())) {
			if (serverDisconnectAction != null) {
				serverDisconnectAction.act(this, oldClient);
			}
			removeServerClient(oldClient);
		}
		if (clients.containsKey(oldClient.ip())) {
			if (clientDisconnectAction != null) {
				clientDisconnectAction.act(this, oldClient);
			}
			removeConnection(oldClient);
		}
	}

	/**
	 * Adds a type of packet that can be sent over the network. This is needed
	 * because the NetworkIO object needs a reference to a Packet to use its
	 * construct() method.
	 */
	public void addPacketType(Packet p) {
		packetTypes.put(p.getPacketType(), p);
	}

	/**
	 * Searches for the first instance of a packet in a client's queue.
	 * 
	 * @param target
	 * @param queue
	 * @return the first instance of that type of packet in a client's queue
	 */
	@SuppressWarnings("unchecked")
	public <T extends Packet> T findFirstInstanceOf(T target, LinkedList<Packet> queue) {
		if (queue == null)
			return null;
		for (Packet p : queue) {
			try {
				if (target.getPacketType().equals(p.getPacketType())) {
					return (T) p;
				}
			} catch (ClassCastException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Searches for the first instance of a packet in a client's queue, then removes
	 * it.
	 * 
	 * @param <T>
	 * @param target
	 * @param queue
	 * @return the first instance of that type of packet in a client's queue
	 */
	public <T extends Packet> T pollFirstInstanceOf(T target, LinkedList<Packet> queue) {
		T found = findFirstInstanceOf(target, queue);
		if (found == null)
			return null;
		queue.remove(found);
		return found;
	}

	// SERVER METHODS
	/**
	 * Returns all clients currently connected to the internal server associated
	 * with their ip. Clients are not returned in any particular order.
	 */
	public HashMap<String, Client> getServerClients() {
		return serverClients;
	}

	/**
	 * Returns true if the internal server is running.
	 */
	public boolean isServerActive() {
		return server != null;
	}

	/**
	 * Returns true if the given client is a client of the internal server.
	 */
	public boolean isServerClient(Client c) {
		return serverClients.containsKey(c.ip());
	}

	/**
	 * Returns true if the given ip is a client of the internal server.
	 */
	public boolean isServerClient(String ip) {
		return serverClients.containsKey(ip);
	}

	/**
	 * Changes the server port and starts the server on that port.
	 */
	public void startServer(int port) {
		serverPort = port;
		startServer();
	}

	/**
	 * Stops the server if it is running, then starts a new one on the preset server
	 * port.
	 */
	public void startServer() {
		if (isServerActive())
			stopServer();
		server = new Server(parent, serverPort);
	}

	/**
	 * Stops the server and clears all client data.
	 */
	public void stopServer() {
		if (server == null)
			return;
		server.stop();
		server = null;
		serverClients.clear();
		serverBuffers.clear();
		serverData.clear();
	}
	
	public int getServerPort() {
		return this.serverPort;
	}

	/**
	 * Sends a packet to ALL clients connected to the internal server.
	 */
	public void sendToClients(Packet p) {
		if (server == null || p == null)
			return;
		for (String ip : serverClients.keySet()) {
			sendToClient(ip, p);
		}
	}

	/**
	 * Sends a packet to all connected clients except one. This can be used when a
	 * client sends data that needs to be distributed to the other connected
	 * clients.
	 */
	public void sendToClientsExcept(String except, Packet p) {
		if (server == null || p == null || except == null)
			return;
		for (String ip : serverClients.keySet()) {
			if (!ip.equals(except)) {
				sendToClient(ip, p);
			}
		}
	}

	/**
	 * Sends a packet to a specific client that is connected to the internal server
	 * by ip. If the ip is found to be in this NetworkIO's external connections, it
	 * will write directly to its buffer to avoid circular connections.
	 */
	public void sendToClient(String ip, Packet p) {
		if (server == null || ip == null || !serverClients.containsKey(ip))
			return;
		if (hasConnection(ip) || isLocalHost(ip)) { // If this machine is connected to itself
			// direct write
			readFromBytes(formatForTransfer(p), clientBuffers.get(ip), clientData.get(ip));
			return;
		}
		Client c = serverClients.get(ip);
		c.write(formatForTransfer(p));
	}

	public void sendToClient(Client c, Packet p) {
		if (server == null || c == null || p == null)
			return;
		sendToClient(c.ip(), p);
	}

	/**
	 * Returns the next available client connected to the server with new data. Can
	 * return null. Here is a simple way to handle multiple clients' data in the
	 * draw loop: for(Client nextClient = NetIO.nextServerClient(); nextClient !=
	 * null; nextClient = NetIO.nextServerClient()) {
	 * NetIO.readFromServerClient(nextClient); }
	 */
	public Client nextServerClient() {
		if (server == null)
			return null;
		return server.available();
	}

	/**
	 * Reads data from a client connected to the internal server. Complete packets
	 * are stored in the client's associated queue.
	 */
	public void readFromServerClient(String ip) {
		if (ip == null || !serverClients.containsKey(ip))
			return;
		readFromServerClient(serverClients.get(ip));
	}

	public void readFromServerClient(Client c) {
		if (c == null || !serverClients.containsKey(c.ip()))
			return;
		ByteQueue buffer = serverBuffers.get(c.ip());
		LinkedList<Packet> data = serverData.get(c.ip());
		readFromClient(c, buffer, data);
	}

	/**
	 * Returns the queue of received packets associated with a client connected to
	 * the internal server. If the ip or client does not match any connected clients
	 * or is null, an empty queue is returned.
	 */
	public LinkedList<Packet> getServerClientData(String ip) {
		if (ip == null)
			return new LinkedList<Packet>();
		if (serverClients.containsKey(ip)) {
			return serverData.get(ip);
		}
		return new LinkedList<Packet>();
	}

	public LinkedList<Packet> getServerClientData(Client c) {
		if (c == null)
			return new LinkedList<Packet>();
		return getServerClientData(c.ip());
	}

	/**
	 * Returns a Map of all queues associated with ips.
	 */
	public HashMap<String, LinkedList<Packet>> getAllServerClientData() {
		return serverData;
	}

	// CLIENT METHODS
	/**
	 * Returns all external connections.
	 */
	public HashMap<String, Client> getConnections() {
		return clients;
	}

	/**
	 * Returns true if connected to any external server.
	 */
	public boolean hasConnection() {
		return !clients.isEmpty();
	}

	/**
	 * Returns true if connected to external server with the provided ip.
	 */
	public boolean hasConnection(String ip) {
		if (ip == null)
			return false;
		return clients.containsKey(ip);
	}

	public boolean hasConnection(Client c) {
		if (c == null)
			return false;
		return hasConnection(c.ip());
	}

	/**
	 * Attempts to connect to a server. Returns true if the connection was
	 * successful. Clears client data if client was already connected to the server.
	 * <br>
	 * Note: Connecting to Server.ip() will likely change the ip to 127.0.0.1
	 * (localhost) to prevent circular connections.
	 */
	public boolean connectToServer(String ip, int port) {
		if (ip == null || port < 0)
			return false;
		if (ip == Server.ip()) {
			// check if connecting to itself, then use localhost ip
			// this is because the ip needs to be localhost for the NetworkIO to detect a
			// circular connection
			ip = "127.0.0.1";
		}
		try {
			Client client = new Client(parent, ip, port); // BUG: can freeze program if connection issues arise
			if (!client.active()) { // check for connection issues
				client.stop();
				return false;
			}
			clients.put(client.ip(), client);
			clientBuffers.put(client.ip(), new ByteQueue(clientBufferSize));
			clientData.put(client.ip(), new LinkedList<Packet>());
			return true;
		} catch (Exception e) {
			// clear data if partially initialized
			clients.remove(ip);
			clientBuffers.remove(ip);
			clientData.remove(ip);
			return false;
		}
	}

	public void disconnectFromServer(String ip) {
		if (ip == null)
			return;
		if (!clients.containsKey(ip))
			return;
		clients.get(ip).stop();
		removeConnection(clients.get(ip));
	}

	public void disconnectFromAllServers() {
		for (String ip : clients.keySet()) {
			disconnectFromServer(ip);
		}
	}

	/**
	 * Sends a packet to a server if there is a connection to it. If the server is
	 * found to be part of this object, write directly to avoid circular
	 * connections.
	 */
	public void sendToServer(String ip, Packet p) {
		if (ip == null || p == null)
			return;
		if (!clients.containsKey(ip))
			return;
		if (isServerClient(ip) || isLocalHost(ip)) { // if this connection is a client of this machine
			// write data directly
			readFromBytes(formatForTransfer(p), serverBuffers.get(ip), serverData.get(ip));
			return;
		}
		clients.get(ip).write(formatForTransfer(p));
	}

	public void sendToServer(Client c, Packet p) {
		if (c == null)
			return;
		sendToServer(c.ip(), p);
	}

	/**
	 * Sends a packet to all external connections.
	 * 
	 * @param p
	 */
	public void sendToAllServers(Packet p) {
		for (String ip : clients.keySet()) {
			sendToServer(ip, p);
		}
	}

	/**
	 * Sends a packet to all external connections except one (if it is one of this
	 * object's external connections). This is useful for distributing data to other
	 * servers after receiving data from one.
	 * 
	 * @param except
	 * @param p
	 */
	public void sendToAllServersExcept(String except, Packet p) {
		if (except == null || p == null)
			return;
		for (String ip : clients.keySet()) {
			if (!ip.equals(except)) {
				sendToServer(ip, p);
			}
		}
	}

	/**
	 * Reads data from a connection to an external server. Complete packets are
	 * stored in the connection's queue.
	 */
	public void readFromConnection(String ip) {
		if (ip == null || !clients.containsKey(ip))
			return;
	}

	public void readFromConnection(Client c) {
		if (c == null || !clients.containsKey(c.ip()))
			return;
		ByteQueue buffer = clientBuffers.get(c.ip());
		LinkedList<Packet> data = clientData.get(c.ip());
		readFromClient(c, buffer, data);
	}

	public void readFromAllConnections() {
		for (Client c : clients.values()) {
			readFromConnection(c);
		}
	}

	/**
	 * Returns the queue of received packets associated with a connection to an
	 * external server. If the ip or client does not match any connections or is
	 * null, an empty queue is returned.
	 */
	public LinkedList<Packet> getConnectionData(String ip) {
		if (ip == null)
			return new LinkedList<Packet>();
		if (clients.containsKey(ip)) {
			return clientData.get(ip);
		}
		return new LinkedList<Packet>();
	}

	public LinkedList<Packet> getConnectionData(Client c) {
		if (c == null)
			return new LinkedList<Packet>();
		return getConnectionData(c.ip());
	}

	/**
	 * Returns a Map of all queues associated with external connections and their
	 * IP's
	 */
	public HashMap<String, LinkedList<Packet>> getAllConnectionData() {
		return clientData;
	}

	public boolean isLocalHost(String ip) {
		return ip.equals("localhost") || ip.equals("127.0.0.1");
	}

	// PRIVATE METHODS
	/**
	 * Adds extra data to a packet's getData() method to prepare it for sending on
	 * the network.
	 */
	private byte[] formatForTransfer(Packet p) {
		return ByteOps.combine((p.getPacketType() + "\n").getBytes(), p.getPacketData(), ENDSEQUENCE);
	}

	/**
	 * Checks a buffer for completed Packets, removes the used data from the buffer,
	 * then moves the Packets into a queue.
	 */
	private void readFromBuffer(ByteQueue dataBuffer, LinkedList<Packet> queue) {
		int packetBytes;
		for (packetBytes = dataBuffer.firstIndexOf(ENDSEQUENCE); packetBytes > -1; packetBytes = dataBuffer
				.firstIndexOf(ENDSEQUENCE)) {
			// If the body of this loop is executed, that means there is an endsequence in
			// the buffer
			// But, only read a packet if there is data before it, specifically at least one
			// byte of data for the newline character
			if (packetBytes > 1) {
				// If the body continues, that means there is at least one full packet ready to
				// be read and removed from the buffer
				int nameBytes = dataBuffer.firstIndexOf((byte) '\n');
				if (nameBytes > 1) { // Name cannot be empty
					String packetType = new String(dataBuffer.poll(nameBytes));
					dataBuffer.deQueue(1); // remove newline character from the buffer without returning a value (more
											// performant)
					byte[] data = dataBuffer.poll(packetBytes - nameBytes - 1);
					Packet output = constructPacket(packetType, data);
					if (output == null)
						return;
					queue.offer(output);
				}
			}
			dataBuffer.deQueue(ENDSEQUENCE.length);
		}
	}

	/**
	 * Constructs a packet using the given type and data according to the packets in
	 * the packetTypes map. The design of this class makes the return statement
	 * really satisfying (idk if that's weird).
	 */
	private Packet constructPacket(String type, byte[] data) {
		if (type == null || type.isEmpty() || data == null || !packetTypes.containsKey(type))
			return null;
		return packetTypes.get(type).construct(data);
	}

	/**
	 * Takes all available data from a client and adds it to a data buffer. Then,
	 * move complete packets to the queue to avoid overflowing the buffer.
	 */
	private void readFromClient(Client c, ByteQueue buffer, LinkedList<Packet> data) {
		if (c == null || buffer == null || data == null)
			return;
		while (c.available() > buffer.unusedLength() && buffer.unusedLength() > 0) {
			// buffer will overflow, so try to process data before sending the whole thing
			// but make sure to exit the loop if the buffer is full, because there's nothing
			// you can do
			buffer.add(c.readBytes(buffer.unusedLength()));
			readFromBuffer(buffer, data);
		}
		// add the rest of the data, or all of the data if the buffer limit is not
		// exceeded.
		buffer.add(c.readBytes());
		readFromBuffer(buffer, data);
	}

	private void readFromBytes(byte[] bytes, ByteQueue buffer, LinkedList<Packet> data) {
		if (bytes == null || buffer == null || data == null)
			return;
		while (bytes.length > buffer.unusedLength() && buffer.unusedLength() > 0) {
			buffer.add(ByteOps.range(bytes, 0, buffer.unusedLength()));
			bytes = ByteOps.shortenFromFront(bytes, buffer.unusedLength());
			readFromBuffer(buffer, data);
		}
		buffer.add(bytes);
		readFromBuffer(buffer, data);
	}

	/**
	 * Adds a client to the internal server. Creates new maps, buffers, and queues.
	 */
	private void addClient(Client c) {
		if (c == null || serverClients.containsKey(c.ip()))
			return;
		serverClients.put(c.ip(), c);
		serverBuffers.put(c.ip(), new ByteQueue(serverBufferSize));
		serverData.put(c.ip(), new LinkedList<Packet>());
	}

	/**
	 * Removes a client from the internal server. Removes all data associated with
	 * that client.
	 */
	private void removeServerClient(Client c) {
		if (c == null || !serverClients.containsKey(c.ip()))
			return;
		serverClients.remove(c.ip());
		serverBuffers.remove(c.ip());
		serverData.remove(c.ip());
	}

	private void removeConnection(Client c) {
		if (c == null || !clients.containsKey(c.ip()))
			return;
		clients.remove(c.ip());
		clientBuffers.remove(c.ip());
		clientData.remove(c.ip());
	}

	private void findPublicIP() {
		BufferedReader in = null;
		try {
			URL whatismyip = new URL("http://checkip.amazonaws.com");
			in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
			publicIP = in.readLine(); // you get the IP as a String
		} catch (Exception e) {
			publicIP = null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
