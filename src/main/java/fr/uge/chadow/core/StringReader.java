package fr.uge.chadow.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class StringReader implements Reader<String> {
	
	private enum State {
		DONE, WAITING, ERROR
	}
	
	private State state = State.WAITING;
	private final int maxStringSize = 1024;
	private final IntReader intReader = new IntReader();
	private final ByteBuffer internalBuffer = ByteBuffer.allocate(maxStringSize);
	private int expectedSize = -1;
	private String value;	
	
	@Override
	public ProcessStatus process(ByteBuffer buffer) {
		if(state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if(expectedSize == -1) {
			var result = intReader.process(buffer);
			if(result != ProcessStatus.DONE) {
				return result;
			}
			expectedSize = intReader.get();
			if(expectedSize > 1024 || expectedSize < 0) {
				return ProcessStatus.ERROR;
			}
			internalBuffer.limit(expectedSize);
		}
		buffer.flip();
		var canRead = Math.min(buffer.remaining(), internalBuffer.remaining());
		var oldlimit = buffer.limit();
		buffer.limit(buffer.position() + canRead);
		internalBuffer.put(buffer);
		buffer.limit(oldlimit);
		buffer.compact();
		if(internalBuffer.hasRemaining()) {
			return ProcessStatus.REFILL;
		}
		state = State.DONE;
		internalBuffer.flip();
		value = StandardCharsets.UTF_8.decode(internalBuffer).toString();
		return ProcessStatus.DONE;
	}

	@Override
	public String get() {
		if(state != State.DONE) {
			throw new IllegalStateException();
		}
		return value;
	}

	@Override
	public void reset() {
		state = State.WAITING;
		expectedSize = -1;
		intReader.reset();
		internalBuffer.clear();
	};
	
}
