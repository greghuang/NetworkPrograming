import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.protobuf.ByteString;

import protocol.Protocol;
import protocol.Protocol.ClientNodes;
import protocol.Protocol.Node;
import protocol.Protocol.Relay;

public class RequestProcessor implements Runnable {
    private static List<SelectionKey> pool = new LinkedList<SelectionKey>();
    private ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
    private static final Integer TYPE_RELAY = new Integer(1);
    private static final Integer TYPE_CLIENT = new Integer(2);
    private static final Integer TYPE_SERVER = new Integer(3);
    
    private TClient owner = null;
    
    public RequestProcessor (TClient owner) {
    	this.owner = owner; 
    }
            
    public static void processRequest(SelectionKey key) {
        synchronized(pool) {
            pool.add(pool.size(), key);
            pool.notifyAll();
        }
    }    

    @Override
    public void run() {
        while (true) {
        	SelectionKey key;
            synchronized(pool) {
                while (pool.isEmpty()) {
                    try {
                        pool.wait();                        
                    }
                    catch (InterruptedException e) {                    	
                    }
                }                
                key = pool.remove(0);             
            }                
            
            try {
				if (key.isValid()) {
					key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
					
					SocketChannel sc = (SocketChannel)key.channel();
					
					buffer.clear();
					
					int count = sc.read(buffer);
										
					if (count > 0) {
						if (key.attachment() == null) {
							System.out.println("Parsing data...");
							parseData(buffer, key);
						}
						
						buffer.flip();						
						if (key.attachment() == TYPE_CLIENT) {
							writeDataToRelay(sc);
						} else if (key.attachment() == TYPE_SERVER) {
							Thread.sleep(100);
							readDataFromServer(key);
						} else if (key.attachment() == TYPE_RELAY) {							
							writeDataToConsole(sc);														
						}
						buffer.clear();
					}
					else {
						// EOF
						if (count < 0) {
							System.out.println("Socket close");
							sc.close();
						}
					}
					key.interestOps(key.interestOps() | SelectionKey.OP_READ);
					key.selector().wakeup();
				}
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        } // end while
    }
    
    private void parseData(ByteBuffer buf, SelectionKey key) 
    {
    	key.attach(null);
    	
    	buf.flip();
    	int size = buf.getInt();
    	
    	if (size>100) {
    		// From Client
			key.attach(TYPE_CLIENT);			
			return;
    	}
    	
		byte[] array = new byte[size];
		buf.get(array);
	
		// From server
		try {
			System.out.println("Parsing ClientNodes...");
			ClientNodes.Builder builder = ClientNodes.newBuilder();
			builder.mergeFrom(array);
			builder.build();
			key.attach(TYPE_SERVER);
			return;
		} catch (Exception e) {
			// Do nothing
		}
		
		// From relay
		try {
			System.out.println("Parsing Relay...");
			Protocol.Relay.Builder builder = Protocol.Relay.newBuilder();
			builder.mergeFrom(array);
			builder.build();
			key.attach(TYPE_RELAY);
			return;
		} catch (Exception e) {
		}				
    }
    
    private void readDataFromServer(SelectionKey key) throws Exception {
    	SocketChannel sc = (SocketChannel) key.channel();
    	ClientNodes clients = getAllClients(sc);
    	
    	if (clients != null) {
    		for (Protocol.Node node : clients.getNodesList()) {
    			if (node.getConnectivity() == Node.NodeConnectivity.CONNECTED) {
    				connectClient(node.getIp(), node.getPort());
    			} else if ( node.getConnectivity() == Node.NodeConnectivity.DISCONNECTED ) { 
    				// Todo
    			}
    		}
    	}
    }
    
    private void connectClient(String ip, int port) {
    	try {    		
    		if (owner.isClientExist(ip, port)) return;
    		System.out.println("Try connect to a client on " + ip + " with " + port + " port");
    		
    		InetSocketAddress address = new InetSocketAddress(ip, port);
    		SocketChannel sc = SocketChannel.open();
    		sc.configureBlocking(false);
        	sc.connect(address);
        	
        	// Wait the connection ready
        	while (!sc.finishConnect()) {
        		Thread.sleep(50);
        	}
        	
        	owner.addRelayChannel(sc);
        	
        	// Send a notify to other relay
        	Relay.Builder builder = Relay.newBuilder();
        	builder.setFrom("127.0.0.1");
        	builder.setTo("10.1.1.2");
        	ByteString bs = ByteString.copyFrom("Relay handshaking".getBytes());
        	builder.setMessage(bs);
        	Relay message = builder.build();
        	
        	buffer.clear();
        	buffer.putInt(message.getSerializedSize()).put(message.toByteArray());
        	buffer.flip();
        	sc.write(buffer);
                        
            //System.out.println("Connect to a client on " + address + " with " + port + " port");
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    private ClientNodes getAllClients(SocketChannel sc) throws Exception {
		
		ClientNodes clients = null;
		byte[] protobuf = readProtobufData(sc);
			
		if (protobuf == null) {
			System.out.println("buf null");
			return null;
		}

		ClientNodes.Builder builder = ClientNodes.newBuilder();
		builder.mergeFrom(protobuf);
		clients = builder.build();		
		return clients;
	}
    
    private void writeDataToRelay(SocketChannel source) throws Exception{ 	
    	Relay.Builder builder = Relay.newBuilder();
    	builder.setFrom(source.socket().getInetAddress().getHostAddress());
    	builder.setTo("10.1.1.2");
    	ByteString bs = ByteString.copyFrom(buffer);
    	builder.setMessage(bs);
    	Relay message = builder.build();
    	
    	buffer.clear();
    	buffer.putInt(message.getSerializedSize()).put(message.toByteArray());
    	
    	Iterator<SocketChannel> iter = TClient.relayList.iterator();
    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		if (sc != source) {
    			buffer.flip();
    			while (buffer.hasRemaining()) {
    				sc.write(buffer);
    			}
    			System.out.println("Send mesaage "+message.getSerializedSize()+ " bytes to relay "+ sc.socket().getInetAddress().getHostAddress());
    		}    		
    	}
    	buffer.clear();
    }
    
    private void writeDataToConsole(SocketChannel source) throws Exception{
    	byte[] protobuf = readProtobufData(source);
    	
    	if (protobuf == null) {
			System.out.println("buf null");
			return;
		}
    	
    	Relay.Builder builder = Relay.newBuilder();
		builder.mergeFrom(protobuf);
		Relay message = builder.build();
		String s = new String(message.getMessage().toByteArray());
		
		if (s.equals("Relay handshaking")) {
			owner.removeSocketChannel(source);
			owner.addRelayChannel(source);
			System.out.println("it is handshaking, just return");
			return;
		}
		
		buffer.clear();
		buffer.put(message.getMessage().toByteArray());
		
		Iterator<SocketChannel> iter = TClient.scList.iterator();
    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		buffer.flip();
    		while (buffer.hasRemaining()) {
    			sc.write(buffer);
    		}
    		System.out.println(String.format("Send %s (%n bytes) to console %s", s, message.getSerializedSize(), sc.socket().getInetAddress().getHostAddress()));    		
    	}
    	buffer.clear();
    }
    
    private byte[] readProtobufData(SocketChannel sc) throws Exception{
		int size = buffer.getInt();
		byte[] array = new byte[size];
		buffer.get(array);		
		//System.out.println("Protobuf size:" + size+ " "+array.length);
		buffer.clear();
		return array;
    }
    
//    private void writeResponse(boolean success, Packet.Ack.AckType type, BufferedOutputStream conOut) throws IOException {
//        Packet.Ack.Builder ackBuilder = Packet.Ack.newBuilder();
//        ackBuilder.setType(type);
//        ackBuilder.setSuccess(success);
//        Packet.Ack response = ackBuilder.build();
//        response.writeDelimitedTo(conOut);
//        conOut.flush();        
//    }
//    
//    private Packet.Block getFileBlock(BufferedInputStream in ) throws IOException {
//        Packet.Block.Builder blockBuilder = Packet.Block.newBuilder();
//        blockBuilder.mergeDelimitedFrom(in);
//        Packet.Block block = blockBuilder.build();
//        System.out.println(String.format("Receive a new block(Seq:%d Size:%d Digest:%s EOF:%s)", 
//                block.getSeqNum(), block.getSize(), block.getDigest(), block.getEof()));
//        
//        return block;
//    }
}
