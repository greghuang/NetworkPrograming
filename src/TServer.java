import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class TServer {   
    public static final int SERVER_PORT = 2010;
    public static final int SERVER_TIMEOUT = 10000;
    private final int MAX_NUM_THREAD = 5;
    private Selector selector = null;
    public static List<SocketChannel> clientPool = new LinkedList<SocketChannel>();
    
    public TServer() throws Exception{    	
        for (int i = 0; i < MAX_NUM_THREAD; i++) {
            Thread t = new Thread(new RequestProcessor());
            t.start();
        }
        System.out.println("Create "+MAX_NUM_THREAD+" threads as worker thread...done");
    }
    
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
    					System.out.println("Accept a connection from " + sc);    					
    				}
    				// Read the data
    				else if (key.isReadable()) {
    					RequestProcessor.processRequest(key);
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
