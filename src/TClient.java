import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import protocol.Protocol;

public class TClient {
    public static final String host = "localhost";
    public static final int CLIENT_PORT = 1006;
    public static List<SocketChannel> clientPool = new LinkedList<SocketChannel>();
    private final int MAX_NUM_PROCESSOR = 5;
    private Selector selector = null;
    private SocketChannel srvChannel = null;
    private boolean isServerReady = false;    
    private ByteBuffer cltBuf = ByteBuffer.allocateDirect(1024);
    
    public TClient(String[] args) throws Exception{
    	// Create a selector
    	selector = Selector.open();
    	// Connect to server
    	connectServer(args);
    	// Create request processor
    	for (int i = 0; i < MAX_NUM_PROCESSOR; i++) {
            Thread t = new Thread(new RequestProcessor());
            t.start();
        }
    	
    	cltBuf.clear();
    }
    
    private void writeDataToChannel (SocketChannel channel, ByteBuffer buf) throws Exception{
    	while (buf.hasRemaining()) {
    		channel.write(buf);			
		}
    }
    
    private void readDataFromServer (SelectionKey key) throws Exception {
    	SocketChannel sc = (SocketChannel) key.channel();
    	InputStream in = Channels.newInputStream(sc);
    	
    	
    	int count = 0;
    	
    	cltBuf.clear();
    	
    	while ((count = sc.read(cltBuf)) > 0) {
    		cltBuf.flip();   		
    		cltBuf.clear();
    	}
    	
    	if (count < 0) {
    		sc.close();
    	}
    }
    
	private ServerChange getFileBlock(InputStream in) throws Exception {
		ServerChange.Builder builder = ServerChange.newBuilder();
		
		Packet.Block.Builder blockBuilder = Packet.Block.newBuilder();
		blockBuilder.mergeDelimitedFrom(in);
		Packet.Block block = blockBuilder.build();
		System.out.println(String.format("Receive a new block(Seq:%d Size:%d Digest:%s EOF:%s)", 
				block.getSeqNum(), block.getSize(), block.getDigest(), block.getEof()));

		return block;
	}
    
    private void addClient (SocketChannel sc) throws Exception {
    	sc.configureBlocking(false);	
    	sc.register(selector, SelectionKey.OP_READ);    	
    	clientPool.add(sc);
    	System.out.println("add a connection from " + sc.socket().getInetAddress().getHostAddress());
    }
    
    private boolean isClientExist(SocketChannel target) {
    	Iterator<SocketChannel> iter = clientPool.iterator();

    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		if (sc == target) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public void serveRequest() throws Exception{
    	if (!isServerReady) {
    		System.out.println("Server is not ready, exit now");
    		return;
    	}
    	    	
    	ServerSocketChannel ssc = ServerSocketChannel.open();
    	ssc.configureBlocking(false);
    	ServerSocket ss = ssc.socket();
    	InetSocketAddress address = new InetSocketAddress(CLIENT_PORT);
    	ss.bind(address);
    	ssc.register(selector, SelectionKey.OP_ACCEPT);
    	System.out.println("wait to console on " + CLIENT_PORT + "...");
    	
    	try {
    		while (true) {			
    			int num = selector.select();    	
    			
    			// nothing to do
    			if (num == 0) continue;
    				

    			Set<SelectionKey> selectedKeys = selector.selectedKeys();
    			Iterator<SelectionKey> it = selectedKeys.iterator();

    			while (it.hasNext()) {
    				SelectionKey key = (SelectionKey) it.next();

    				// Is it a new connection?
    				if ( key.isAcceptable() ) {					
    					ServerSocketChannel svrSocketChannel = (ServerSocketChannel) key.channel();
    					SocketChannel sc = svrSocketChannel.accept();
    					
    					if (!isClientExist(sc)) { 
    						addClient(sc);
    					}
    				}
    				// Read the data
    				else if (key.isReadable()) {
    					System.out.println ("A readable key is coming");
    					   					
    					boolean isFromServer = (key.channel() == srvChannel);
    					
    					if (isFromServer)
    						readDataFromServer(key);
    					else
    						RequestProcessor.processRequest(key);
    				}
    				
    				it.remove();
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();    		
    	}
    	finally {
    		try {
    			if (srvChannel != null) srvChannel.close();
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}
	}
    
    private void connectClient(String ip, int port) {
    	try {
    		InetSocketAddress address = new InetSocketAddress(ip, port);
    		SocketChannel sc = SocketChannel.open();
        	sc.configureBlocking(false);
        	sc.connect(address);
        	
        	// Wait the connection ready
        	while (!sc.finishConnect()) {
        		Thread.sleep(50);
        	}
        	
        	sc.register(selector, SelectionKey.OP_READ);
        	
        	clientPool.add(sc);
                        
            System.out.println("Connect to client on " + address + " with " + port + " port");
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    private void removeClient(String ip) {
    	Iterator<SocketChannel> iter = TServer.clientPool.iterator();
    	System.out.println("Host:"+ host);
    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		if (sc.socket().getInetAddress().getHostAddress().equals(ip)) {
    			// Todo
    		}
    			
    	}
    }
    
    private void connectServer(String[] args){        
        try {        	
        	InetSocketAddress address = new InetSocketAddress(args[0], TServer.SERVER_PORT);
        	srvChannel = SocketChannel.open();
        	srvChannel.configureBlocking(false);
        	srvChannel.connect(address);
        	
        	// Wait the connection ready
        	while (!srvChannel.finishConnect()) {
        		Thread.sleep(50);
        	}        	
        	srvChannel.register(selector, SelectionKey.OP_READ);        	
            isServerReady = true;
            
            System.out.println("Connect to server on " + address);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public static void main(String[] args) {
    	if (args == null || args[0] == null) {
    		System.out.println("Missing server IP, e.g TClient 10.1.100.99");
    		return;
    	}
    	
        try {
            new TClient(args).serveRequest();
        }
        catch (Exception e) {
            System.out.println("Fatal:"+e.getMessage());
        }
        System.out.println("Client is terminated");
    }
}
