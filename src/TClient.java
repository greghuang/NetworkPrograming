import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import protocol.Protocol.ClientNodes;
import protocol.Protocol.Node;

public class TClient {
    public static final String host = "localhost";
    public static final int DEFAULT_CLIENT_PORT = 1006;
    public static List<SocketChannel> clientPool = new LinkedList<SocketChannel>();
    private final int MAX_NUM_PROCESSOR = 5;
    private int socket_port = DEFAULT_CLIENT_PORT;
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
    	ClientNodes clients = getAllClients(in);
    	
    	for (Protocol.Node node : clients.getNodesList()) {
    		if (node.getConnectivity() == Node.NodeConnectivity.CONNECTED) {
    			connectClient(node.getIp(), node.getPort());
    		} else if ( node.getConnectivity() == Node.NodeConnectivity.DISCONNECTED ) { 
    			removeClient(node.getIp());
    		}    			
    	}
    }
    
	private ClientNodes getAllClients(InputStream in) throws Exception {
		ClientNodes.Builder builder = ClientNodes.newBuilder();
		builder.mergeDelimitedFrom(in);
		ClientNodes clients = builder.build();
		
//		System.out.println(String.format("Receive a new block(Seq:%d Size:%d Digest:%s EOF:%s)", 
//				block.getSeqNum(), block.getSize(), block.getDigest(), block.getEof()));

		return clients;
	}
    
    private void addClient (SocketChannel sc) throws Exception {
    	sc.configureBlocking(false);	
    	sc.register(selector, SelectionKey.OP_READ);    	
    	clientPool.add(sc);
    	System.out.println("add a connection from " + sc.socket().getInetAddress().getHostAddress());
    }
    
    private void connectClient(String ip, int port) {
    	try {    		
    		if (isClientExist(ip, port)) return;
    		
    		InetSocketAddress address = new InetSocketAddress(ip, port);
    		SocketChannel sc = SocketChannel.open();
        	sc.connect(address);
        	
        	// Wait the connection ready
        	while (!sc.finishConnect()) {
        		Thread.sleep(50);
        	}
        	
        	addClient(sc);			
                        
            System.out.println("Connect to a client on " + address + " with " + port + " port");
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
    
    private boolean isClientExist(String ip, int port) {
    	Iterator<SocketChannel> iter = clientPool.iterator();

    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		Socket so = sc.socket();
    		if (ip == so.getInetAddress().getHostAddress() && port == so.getPort()) {
    			return true;
    		}
    	}
    	return false;
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
        	Protocol.Node.Builder builder = Protocol.Node.newBuilder();        	
        	InetAddress localhost = InetAddress.getLocalHost();
        	builder.setIp(localhost.getHostAddress());
        	builder.setPort(socket_port);
        	builder.setConnectivity(Node.NodeConnectivity.CONNECTED);
        	Protocol.Node node = builder.build();
        	node.writeDelimitedTo(Channels.newOutputStream(srvChannel));
        	
            isServerReady = true;
            
            System.out.println("Connect to server on " + address);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public static void main(String[] args) {
    	if (args == null || args.length != 2) {
    		System.out.println("Missing server IP, e.g. TClient 10.1.100.99 6666");
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
