package networkObjects;

/**
 * A Packet is an object able to be stored as a byte[] and reconstructed from a
 * byte[]. The Packet must also have a unique identifier returned with
 * getPacketType().
 */
public interface Packet {
	/**
	 * The unique type of this packet. Should be constant and not contain newline
	 * characters.
	 * 
	 * @return the type of this packet as a String
	 */
	String getPacketType();

	/**
	 * @return a byte[] containing data from which this packet can be recreated
	 */
	byte[] getPacketData();

	/**
	 * The way to reconstruct a new packet from a byte[]. Should not modify the
	 * current reference.
	 * 
	 * @param data
	 * @return a new Packet Object
	 */
	Packet construct(byte[] data);
}
