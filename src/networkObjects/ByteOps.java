package networkObjects;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A class with utilities to convert primitives and Strings to and from byte
 * arrays, and to modify existing byte arrays.
 * 
 * @author Yehoshua Halle
 *
 */
public class ByteOps {
	public static byte[] toByteArray(boolean value) {
		return new byte[] { value ? (byte) 1 : (byte) 0 };
	}

	public static boolean toBoolean(byte[] data) {
		return toBoolean(data[0]);
	}

	public static boolean toBoolean(byte data) {
		return data == (byte) 1 ? true : false;
	}

	public static boolean readBoolean(byte[] buffer) {
		return toBoolean(buffer);
	}

	public static boolean readBoolean(byte buffer) {
		return toBoolean(buffer);
	}

	public static byte[] toByteArray(byte value) {
		return new byte[] { value };
	}

	public static byte readByte(byte[] data) {
		return data[0];
	}

	public static byte[] toByteArray(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	public static int toInt(byte[] data) {
		return ByteBuffer.wrap(data).getInt();
	}

	public static int readInt(byte[] buffer) {
		int output = toInt(range(buffer, 0, 4));
		// shortenFromFront(buffer, 4);
		return output;
	}

	public static byte[] toByteArray(float value) {
		return ByteBuffer.allocate(4).putFloat(value).array();
	}

	public static float toFloat(byte[] data) {
		return ByteBuffer.wrap(data).getFloat();
	}

	public static float readFloat(byte[] buffer) {
		float output = toFloat(range(buffer, 0, 4));
		// shortenFromFront(buffer, 4);
		return output;
	}

	public static byte[] toByteArray(char value) {
		return ByteBuffer.allocate(2).putChar(value).array();
	}

	public static char toChar(byte[] data) {
		return ByteBuffer.wrap(data).getChar();
	}

	public static char readChar(byte[] buffer) {
		char output = toChar(range(buffer, 0, 2));
		// shortenFromFront(buffer, 2);
		return output;
	}

	public static byte[] toByteArray(long value) {
		return ByteBuffer.allocate(8).putLong(value).array();
	}

	public static long toLong(byte[] data) {
		return ByteBuffer.wrap(data).getLong();
	}

	public static long readLong(byte[] buffer) {
		long output = toLong(range(buffer, 0, 8));
		// shortenFromFront(buffer, 8);
		return output;
	}

	public static byte[] toByteArray(double value) {
		return ByteBuffer.allocate(8).putDouble(value).array();
	}

	public static float toDouble(byte[] data) {
		return ByteBuffer.wrap(data).getLong();
	}

	public static double readDouble(byte[] buffer) {
		double output = toDouble(range(buffer, 0, 8));
		// shortenFromFront(buffer, 8);
		return output;
	}

	public static byte[] toByteArray(String value) {
		return value.getBytes();
	}

	public static String toString(byte[] data) {
		return new String(data);
	}

	/**
	 * Reads the byte array until the end character, then returns the array data as
	 * a string up to but not including the end character
	 */
	public static String readStringUntil(byte[] buffer, int end) {
		byte[] data = readBytesUntil(buffer, end);
		return toString(data);
	}

	/**
	 * Reads the byte array until the end byte, then returns the array data up to
	 * but not including the end character
	 */
	public static byte[] readBytesUntil(byte[] buffer, int end) {
		byte target = (byte) end;
		int endIndex = -1;
		for (int i = 0; i < buffer.length; i++) {
			if (buffer[i] == target) {
				endIndex = i;
				break;
			}
		}
		if (endIndex == -1)
			return null;
		byte[] output = new byte[endIndex];
		System.arraycopy(buffer, 0, output, 0, endIndex);
		// shortenFromFront(buffer, endIndex + 1);
		return output;
	}

	/**
	 * Combines an arbitrary number of byte arrays into a single byte array.
	 */
	public static byte[] combine(byte[]... elements) {
		if (elements.length == 0) {
			return new byte[0];
		}
		int totalLength = 0;
		for (int i = 0; i < elements.length; i++) {
			totalLength += elements[i].length;
		}
		byte[] output = Arrays.copyOf(elements[0], totalLength);
		int nextStartIndex = elements[0].length;
		for (int i = 1; i < elements.length; i++) {
			System.arraycopy(elements[i], 0, output, nextStartIndex, elements[i].length);
			nextStartIndex += elements[i].length;
		}
		return output;
	}

	/**
	 * Combines an arbitrary number of objects into a combined byte array as long as
	 * each object can be converted into a byte array
	 */
	public static byte[] combine(Object... elements) {
		//byte[] output = new byte[0];
		throw new UnsupportedOperationException("Not implemented yet.");
	}

	/**
	 * Creates a new byte array based on the bytes within a range of the original.
	 */
	public static byte[] range(byte[] source, int start, int end) {
		if (source == null)
			return null;
		return Arrays.copyOfRange(source, start, end);
	}

	/**
	 * Removes the numBytes from the beginning of an array, returns a new list with
	 * the remaining data.
	 */
	public static byte[] shortenFromFront(byte[] buffer, int numBytes) {
		if (numBytes <= 0)
			return new byte[0];
		numBytes = Math.min(numBytes, buffer.length);
		byte[] newArray = new byte[buffer.length - numBytes];
		System.arraycopy(buffer, numBytes, newArray, 0, newArray.length);
		return newArray;
	}

	/**
	 * The slices list is an arbitrarily long list of lengths. If you were to slice
	 * the list {0, 1, 2, 3, 4, 5, 6, 7, 8, 9} with the slices {2, 5, 3}, the output
	 * array would contain (in order) {0, 1} {2, 3, 4, 5, 6} {7, 8, 9} This does not
	 * check for bounds. This method is good for slicing a large byte array into
	 * multiple smaller byte arrays; Perfect for Packet.construct(byte[] data)
	 * methods.
	 */
	public static byte[][] slice(byte[] source, int... slices) {
		byte[][] output = new byte[slices.length][];
		int prevIndex = 0;
		for (int i = 0; i < slices.length; i++) {
			output[i] = range(source, prevIndex, prevIndex + slices[i]);
			prevIndex += slices[i];
		}
		return output;
	}
}