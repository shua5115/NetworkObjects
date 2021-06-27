package networkObjects;

import java.util.Iterator;

/**
 * A squirrel cage buffer implementation for bytes. Not designed for use outside of the NetworkIO class, but it is useful.
 * @author Yehoshua Halle
 *
 */
class ByteQueue implements Iterable<Byte> {
	private byte[] buffer;
	private int startIndex;
	private int length;

	public ByteQueue(int capacity) {
		buffer = new byte[capacity];
		startIndex = 0;
		length = 0;
	}

	public int length() {
		return length;
	}

	public void clear() {
		buffer = new byte[buffer.length];
		startIndex = 0;
		length = 0;
	}

	public int unusedLength() {
		return buffer.length - length;
	}

	public ByteQueue add(byte b) {
		return add(new byte[] { b });
	}

	public ByteQueue add(byte[] b) {
		if (b == null || b.length == 0)
			return this;
		if (length == buffer.length) {
			System.err.println("ByteQueue buffer is full, all data was discarded.");
			return this;
		}
		// limit the copied data of b to the length of unused data in the buffer
		int unusedLength = buffer.length - length;
		if (b.length > unusedLength)
			System.err.println("ByteQueue buffer has filled, some data was discarded.");
		int inLength = Math.min(b.length, unusedLength); // the total length of data to be copied
		int copyPointer = (startIndex + length) % buffer.length; // the point in the buffer at which to copy the data
		int wrapAround = (copyPointer + inLength) / buffer.length; // will be 1 if the new data would exceed the
																	// buffer's length
		int inPointer = 0;
		// Limit first copy to only copy data until the end of the list
		int copyLength = Math.min(inLength, buffer.length - copyPointer);
		System.arraycopy(b, inPointer, buffer, copyPointer, copyLength);
		if (wrapAround > 0) {
			inPointer += copyLength;
			copyLength = inLength - copyLength;
			System.arraycopy(b, inPointer, buffer, 0, copyLength);
		}
		length += inLength;
		return this;
	}

	// Removes a certain number of bytes from the front of the queue without
	// returning a value.
	public void deQueue(int numBytes) {
		int toRemove = Math.min(numBytes, length);
		startIndex = (startIndex + toRemove) % buffer.length;
		length -= toRemove;
	}

	public byte[] peek(int numBytes) {
		int outLength = Math.min(Math.min(numBytes, length), buffer.length);
		byte[] output = new byte[outLength];
		if (outLength == 0)
			return output;
		// int neededLength = outLength;
		int nextInsert = 0;
		int wrapAround = (startIndex + outLength) / buffer.length; // number of times the end index exceeds the length
																	// of the buffer. Should never exceed 1.
		int copyLength = Math.min(outLength, buffer.length - startIndex);
		System.arraycopy(buffer, startIndex, output, nextInsert, copyLength);
		// startIndex whould be 0 if copyLength was added to it if wrapAround > 0
		if (wrapAround > 0) {
			nextInsert += copyLength;
			copyLength = outLength - copyLength;
			if (outLength > 0) {
				System.arraycopy(buffer, 0, output, nextInsert, copyLength);
			}
		}
		return output;
	}

	public byte peek() {
		byte[] output = peek(1);
		return output.length > 0 ? output[0] : (byte) 0;
	}

	public byte[] peekAll() {
		return peek(length);
	}

	public byte[] poll(int numBytes) {
		byte[] output = peek(numBytes);
		deQueue(output.length);
		return output;
	}

	public byte poll() {
		byte[] output = poll(1);
		return output.length > 0 ? output[0] : (byte) 0;
	}

	public byte[] pollAll() {
		return poll(length);
	}

	public Iterator<Byte> iterator() {
		return new Iterator<Byte>() {
			private int count = 0;

			public boolean hasNext() {
				return count < length;
			}

			public Byte next() {
				int curIndex = (startIndex + count) % buffer.length;
				Byte output = buffer[curIndex];
				count++;
				return output;
			}
		};
	}

	/**
	 * Looks in the array for a sequence, then returns the index at which the
	 * sequence starts. Returns -1 if the sequence is not found.
	 */
	public int firstIndexOf(byte[] seq) {
		if (seq.length == 0 || seq.length > buffer.length)
			return -1; // impossible
		int streakStart = -1;
		int matched = 0;
		byte[] data = this.peekAll();
		for (int i = 0; i < data.length; i++) {
			byte cur = data[i];
			if (cur == seq[matched]) {
				if (matched == 0) {
					streakStart = i;
				}
				matched++;
			} else {
				streakStart = -1;
				matched = 0;
			}
			if (seq.length == matched) {
				return streakStart;
			}
		}
		return -1;
	}

	public int firstIndexOf(byte b) {
		return firstIndexOf(new byte[] { b });
	}
}