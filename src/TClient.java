import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import protocol.Protocol.Node;

public class TClient {
    public static final String host = "localhost";
    public static final int DEFAULT_CLIENT_PORT = 1006;
    public static List<SocketChannel> scList = new LinkedList<SocketChannel>();
    public static List<SocketChannel> relayList = new LinkedList<SocketChannel>();
        
    private final int MAX_NUM_PROCESSOR = 5;
    private int socket_port = DEFAULT_CLIENT_PORT;
    private Selector selector = null;
    private SocketChannel srvChannel = null;
     
    private boolean isServerReady = false;    
    private ByteBuffer cltBuf = ByteBuffer.allocateDirect(1024);
    
    public TClient() throws Exception{
    	// Create request processor
    	for (int i = 0; i < MAX_NUM_PROCESSOR; i++) {
            Thread t = new Thread(new RequestProcessor(this));
            t.start();
        }
    	
    	cltBuf.clear();
    }
    
    public void addRelayChannel (SocketChannel sc) throws Exception {
    	if (isRelayExist(sc)) return;    	
    	if (!sc.isRegistered()){
    		sc.configureBlocking(false);
    		sc.register(selector, SelectionKey.OP_READ);
    	}    	
    	relayList.add(sc);
    	System.out.println("add a relay connection from " + sc.socket().getInetAddress().getHostAddress());
    }
       
    public void addSocketChannel (SocketChannel sc) throws Exception {
    	sc.configureBlocking(false);	
    	sc.register(selector, SelectionKey.OP_READ);
    	scList.add(sc);
    	System.out.println("add a socket connection from " + sc.socket().getInetAddress().getHostAddress());
    }
    
    public  void removeSocketChannel(SocketChannel target) {
    	Iterator<SocketChannel> iter = scList.iterator();
    	
    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		if (sc == target) {
    			iter.remove();
    		}    			
    	}
    }
    
    public boolean isClientExist(String ip, int port) {
    	Iterator<SocketChannel> iter = scList.iterator();

    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		Socket so = sc.socket();
    		if (ip == so.getInetAddress().getHostAddress() && port == so.getPort()) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public boolean isRelayExist(SocketChannel target) {
    	Iterator<SocketChannel> iter = relayList.iterator();

    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		if (sc == target) {
    			return true;
    		}
    	}
    	return false;
    }

    
    public boolean isClientExist(SocketChannel target) {
    	Iterator<SocketChannel> iter = scList.iterator();

    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		if (sc == target) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public void serveRequest(String[] args) throws Exception {
    	// Create a selector
    	selector = Selector.open();
    	// Connect to server
    	connectServer(args);
    	
    	if (!isServerReady) {
    		System.out.println("Server is not ready, exit now");
    		return;
    	}
    	    	
    	ServerSocketChannel ssc = ServerSocketChannel.open();
    	ssc.configureBlocking(false);
    	ServerSocket ss = ssc.socket();
    	InetSocketAddress address = new InetSocketAddress(socket_port);
    	ss.bind(address);
    	ssc.register(selector, SelectionKey.OP_ACCEPT);
    	System.out.println("wait to console on " + socket_port + "...");
    	
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
    					System.out.println("Accept a new connection");
    					ServerSocketChannel svrSocketChannel = (ServerSocketChannel) key.channel();
    					SocketChannel sc = svrSocketChannel.accept();
    					addSocketChannel(sc);					
    				}
    				// Read the data
    				else if (key.isReadable()) {
    					RequestProcessor.processRequest(key);
    					Thread.sleep(10);    					   					
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
    
    private void connectServer(String[] args){        
        try {
        	socket_port = Integer.parseInt(args[1]);
        	InetSocketAddress address = new InetSocketAddress(args[0], TServer.SERVER_PORT);
        	srvChannel = SocketChannel.open();
        	srvChannel.configureBlocking(false);
        	srvChannel.connect(address);
        	
        	// Wait the connection ready
        	while (!srvChannel.finishConnect()) {
        		Thread.sleep(50);
        	}        	
        	srvChannel.register(selector, SelectionKey.OP_READ);
        	
        	// Send the listening IP and port
        	Node.Builder builder = Node.newBuilder();
        	InetAddress localhost = InetAddress.getLocalHost();
        	builder.setIp(localhost.getHostAddress());
        	builder.setPort(socket_port);
        	builder.setConnectivity(Node.NodeConnectivity.CONNECTED);
        	Node node = builder.build();
        	cltBuf.clear();
        	cltBuf.putInt(node.getSerializedSize()).put(node.toByteArray());
        	//cltBuf.put(node.toByteArray());
        	cltBuf.flip();
        	srvChannel.write(cltBuf);        	
            isServerReady = true;
            
            System.out.println("Connect to server on " + address);            
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public static void main(String[] args) {
    	if (args == null || args.length != 2) {
    		System.out.println("Missing server IP or listening port, e.g. TClient 10.1.100.99 6666");
    		return;
    	}
    	
        try {
            new TClient().serveRequest(args);
        }
        catch (Exception e) {
            System.out.println("Fatal:"+e.getMessage());
        }
        System.out.println("Client is terminated");
    }
}
