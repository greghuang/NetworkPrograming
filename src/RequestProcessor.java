import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class RequestProcessor implements Runnable {
    private static List<SelectionKey> pool = new LinkedList<SelectionKey>();
    private ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            
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
            	key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
                System.out.println("Accept a request from "+ key.channel());
                
            	SocketChannel sc = (SocketChannel) key.channel();
            	int count = 0;    	
            	
            	buffer.clear();
            	
            	while ((count = sc.read(buffer)) > 0) {
            		buffer.flip();
            		writeDataToClients(buffer, sc);
            		//sc.write(buffer);
            		buffer.clear();
            	}
            	
            	// EOF
            	if (count < 0) {
            		System.out.println("Socket close");
            		sc.close();
            		//continue;
            	}
            	
            	 key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            	 key.selector().wakeup();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        } // end while
    }
    
    private void writeDataToClients(ByteBuffer buf, SocketChannel host) throws Exception{
    	Iterator<SocketChannel> iter = TServer.clientPool.iterator();
    	System.out.println("Host:"+ host);
    	while (iter.hasNext()) {
    		SocketChannel sc = iter.next();
    		// Don't send message to self
    		System.out.println("others:"+ sc);
    		if (sc != host) {
    			while (buf.hasRemaining()) {
    				sc.write(buf);
    			}
    		}
    	}    	
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
