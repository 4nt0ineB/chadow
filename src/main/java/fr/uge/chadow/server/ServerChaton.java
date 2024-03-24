package fr.uge.chadow.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.chadow.core.MessageReader;
import fr.uge.chadow.core.Message;
import fr.uge.chadow.core.Reader;

public class ServerChaton {
	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer processingMsg = ByteBuffer.allocate(2*Integer.BYTES + 2 * BUFFER_SIZE);
		private final ArrayDeque<Message> queue = new ArrayDeque<>();
		private final ServerChaton server;  // we could also have Context as an instance class, which would naturally
											// give access to ServerChatInt.this
		private boolean closed = false;
		private final MessageReader messageReader = new MessageReader();
		
		private Context(ServerChaton server, SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = server;
		}

		/**
		 * Process the content of bufferIn
		 *
		 * The convention is that bufferIn is in write-mode before the call to process and
		 * after the call
		 *
		 */
		 private void processIn() {
		    for (;;) {
		      Reader.ProcessStatus status = messageReader.process(bufferIn);
		      switch (status) {
		      case DONE:
		        var value = messageReader.get();
		        server.broadcast(value);
		        messageReader.reset();
		        break;
		      case REFILL:
		        return;
		      case ERROR:
		        silentlyClose();
		        return;
		      }
		    }
		  }
		/**
		 * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
		 *
		 * @param msg
		 */
		public void queueMessage(Message msg) {
			queue.addFirst(msg);
			processOut();
			updateInterestOps();
		}

		/**
		 * Try to fill bufferOut from the message queue
		 *
		 */
		private void processOut() {
			processingMsg.flip();
			if(processingMsg.hasRemaining()) {
				var oldlimit = processingMsg.limit();
				processingMsg.limit(bufferOut.remaining());
				bufferOut.put(processingMsg);
				processingMsg.limit(oldlimit);
				processingMsg.compact();
			}else {
				processingMsg.clear();
				var msg = queue.pollLast();
		        var login = StandardCharsets.UTF_8.encode(msg.login());
		        var txt = StandardCharsets.UTF_8.encode(msg.txt());
		        bufferOut
		        	.putInt(login.remaining()).put(login)
		        	.putInt(txt.remaining()).put(txt);
			}
		    updateInterestOps();
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and of both ByteBuffers.
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * updateInterestOps and after the call. Also it is assumed that process has
		 * been be called just before updateInterestOps.
		 */

		private void updateInterestOps() {
			int ops = 0;
		    if(bufferIn.hasRemaining() && !closed) {
		        ops |= SelectionKey.OP_READ;
		    }
		    if(bufferOut.position() > 0) {
		    	ops |= SelectionKey.OP_WRITE;
		    }
		    if(ops != 0) {
		    	key.interestOps(ops);
		    } else {
		    	silentlyClose();
		    }
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doRead and after the call
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {
			if(sc.read(bufferIn) == -1) {
				closed = true;
				logger.info("Client " + sc.getRemoteAddress() + " has closed the connection");
			}
			processIn();
			updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doWrite and after the call
		 *
		 * @throws IOException
		 */

		private void doWrite() throws IOException {
			bufferOut.flip();
		    sc.write(bufferOut);
		    bufferOut.compact();
		    processIn();
		    updateInterestOps();
		}

	}

	private static final int BUFFER_SIZE = 1_024;
	private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerChaton(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while (!Thread.interrupted()) {
			//Helpers.printKeys(selector); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
	}

	private void treatKey(SelectionKey key) {
		// Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		var sc = serverSocketChannel.accept();
		if(sc == null) {
			logger.warning("selector gave wrong hint for accept");
			return;
		}
		sc.configureBlocking(false);
		var sckey = sc.register(selector, SelectionKey.OP_READ);
		sckey.attach(new Context(this, sckey));
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Add a message to all connected clients queue
	 *
	 * @param msg
	 */
	private void broadcast(Message msg) {
		for(var key: selector.keys()) {
			var context = ((Context) key.attachment());
			if(context != null) {
				context.queueMessage(msg);
			}
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerChaton(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSumBetter port");
	}
}