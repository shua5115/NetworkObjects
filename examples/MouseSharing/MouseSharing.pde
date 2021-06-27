import networkObjects.*;
import processing.net.*;
import java.util.*;
import java.util.Map.Entry;
NetworkIO net = new NetworkIO(this, 25565); // Replace server port with whatever you plan to use
public void setup() {
  size(1280, 720);
  net.addPacketType(new PositionPacket());
  net.startServer();
  background(0);
}
public void keyPressed() {
  if (key == ' ') {
    if (net.hasConnection()) {
      net.disconnectFromAllServers();
    } else {
      // Replace Server.ip() with your public IP and port forwarding to allow anyone to share their cursor
      net.connectToServer(Server.ip(), 25565); // and make sure to change the port if you changed it above
    }
  }
}
public void draw() {
  noStroke();
  fill(0, 10);
  rect(0, 0, width, height);
  fill(0);
  rect(0, 0, 256, 55);
  fill(255);
  text("Connected clients: " + net.getServerClients().size(), 0, 11);
  int nextY = 22;
  for (String ip : net.getServerClients().keySet()) {
    text("From: " + ip, 0, nextY);
    nextY += 11;
  }
  int xOff = 128;
  text("External connections: " + net.getConnections().size(), xOff, 11);
  nextY = 22;
  for (String ip : net.getConnections().keySet()) {
    text("To: " + ip, xOff, nextY);
    nextY += 11;
  }
  // Process connected clients' data
  for (Entry<String, LinkedList<Packet>> e : net.getAllServerClientData().entrySet()) {
    String ip = e.getKey();
    Queue<Packet> q = e.getValue();
    while (q.size() > 0) {
      Packet in = q.poll();
      // Redistribute incoming data
      net.sendToClients(in);
    }
  }
  // Send mouse position to server
  Packet toSend = new PositionPacket(mouseX, mouseY, mousePressed ? 1 : 0);
  net.sendToAllServers(toSend);
  // Process incoming data
  for (Queue<Packet> q : net.getAllConnectionData().values()) {
    while (q.size() > 0) {
      Packet in = q.poll();
      if (in != null) {
        if (in instanceof PositionPacket) {
          PositionPacket pos = (PositionPacket) in;
          stroke(0);
          fill(pos.z == 1.0 ? color(100) : color(255));
          triangle(pos.x, pos.y, pos.x, pos.y + 15, pos.x + 10, pos.y + 10);
        }
      }
    }
  }
}
public void clientEvent(Client c) {
  net.putInClientEvent(c);
}

public void serverEvent(Server s, Client c) {
  net.putInServerEvent(s, c);
}

public void disconnectEvent(Client c) {
  net.putInDisconnectEvent(c);
}

public class PositionPacket implements Packet {
  private float x, y, z;
  public PositionPacket(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
  public PositionPacket() {
    this(0, 0, 0);
  }
  public String getPacketType() {
    return "PositionPacket";
  }
  public byte[] getPacketData() {
    byte[] X = ByteOps.toByteArray(x);
    byte[] Y = ByteOps.toByteArray(y);
    byte[] Z = ByteOps.toByteArray(z);
    byte[] output = ByteOps.combine(X, Y, Z);
    return output;
  }
  public Packet construct(byte[] data) {
    // check data length since, in this case, it should be consistently 12 bytes long
    if (data.length != 12) {
      printArray(data);
      throw new IllegalArgumentException("PositionPacket data does not have the correct length. Length: " + data.length + "; Should be 12.");
    }
    byte[][] input = ByteOps.slice(data, 4, 4, 4);
    float x = ByteOps.toFloat(input[0]);
    float y = ByteOps.toFloat(input[1]);
    float z = ByteOps.toFloat(input[2]);
    return new PositionPacket(x, y, z);
  }
  public String toString() {
    return x + ", " + y + ", " + z;
  }
}
