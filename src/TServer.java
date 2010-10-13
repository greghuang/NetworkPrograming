import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
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
import protocol.Protocol.ClientNodes;
public class TServer {   
    public static final int SERVER_PORT = 2010;
    public static final int SERVER_TIMEOUT = 10000;
    private Selector selector = null;
    private List<SocketChannel> clientPool = new LinkedList<SocketChannel>();
    private ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        
    public void waitRequest() throws IOException {
    	ServerSocketChannel ssc = ServerSocketChannel.open();
    	ssc.configureBlocking( false );
    	ServerSocket srvSocket = ssc.socket();
    	InetSocketAddress address = new InetSocketAddress( SERVER_PORT );
    	srvSocket.bind( address );    	
    	
    	selector = Selector.open();
    	ssc.register( selector, SelectionKey.OP_ACCEPT );
    	
    	System.out.println("Server is listening for connections on port " + srvSocket.getLocalPort() + " ...");
    	
    	try {
            while (true) {
            	int num = selector.select();
            	
            	if (num == 0) continue;
            	
            	Set<SelectionKey> selectedKeys = selector.selectedKeys();
            	Iterator<SelectionKey> it = selectedKeys.iterator();
            	
            	while (it.hasNext()) {
    				SelectionKey key = (SelectionKey) it.next();

    				// Is it a new connection?
    				if ( key.isAcceptable() ) {					
    					ServerSocketChannel svrSocketChannel = (ServerSocketChannel) key.channel();
    					SocketChannel sc = svrSocketChannel.accept();    					    				
    					sc.configureBlocking(false);	
    			    	sc.register(selector, SelectionKey.OP_READ);
    			    	clientPool.add(sc);
    			    	System.out.println("add a connection from " + sc.socket().getInetAddress().getHostAddress());    					
    				}
    				// Read the data
    				else if (key.isReadable()) {
    					//Get just connected Client Information
    					SocketChannel sc = (SocketChannel) key.channel();    					
    					int count = 0;
    					buffer.clear();
    					
    					ClientNodes.Builder clientNodes = ClientNodes.newBuilder();
    					while ((count = sc.read(buffer)) > 0) {
    						buffer.flip();
    						int size = buffer.getInt();
    						byte[] array = new byte[size];
    						buffer.get(array);
    						System.out.println("Protobuf size:" + size);
    						
    						Node.Builder builder = Node.newBuilder();
    						builder.mergeFrom(array);
    						Node node = builder.build();

    						// Write Back Server List.    					
        					
        					clientNodes.addNodes(node);
        					
        					// return client list
        					
    						System.out.println("Build a node ok");    						
    						buffer.clear();
    					}
    					
    					// EOF
    					if (count < 0) {
    						System.out.println("Socket close");
    						sc.close();
    					}
    					else{
    						ClientNodes clientNodes_instance = clientNodes.build();
        					for( SocketChannel client_channel : clientPool){
        						if( client_channel == sc )
        							continue;        						
        						int client_size = clientNodes_instance.getSerializedSize();
        						ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        						buf.clear();
        			        	buf.putInt(client_size ).put(clientNodes_instance.toByteArray());        			      
        			        	buf.flip();
        			        	client_channel.write(buf);
        			        	System.out.println("send a protobuf ok");
        					}    	
    					}
				
    				}
    				
    				it.remove();
    			}        	
            }
    	} catch (Exception e) {
    		e.printStackTrace();    		
    	} finally {
    		try {
    			clientPool.clear();
    			selector.close();
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}
    }
    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            new TServer().waitRequest();
        }
        catch (Exception e) {
            System.out.println(String.format("Fatal:%s", e.getMessage()));
        }
        System.out.println("Server is terminated");
    }
}
