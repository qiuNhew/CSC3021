package uk.ac.qub.csc3021.graph;

import java.util.concurrent.*;
import java.io.*;

public class SparseMatrixPipelined extends SparseMatrix {
    private static final int BLOCK_SIZE = 16384;
    private static final int QUEUE_SIZE = 256;
    
    private int[] index;
    private int[] destination; 
    private int num_vertices;
    private int num_edges;

    private static class EdgeBlock {
        final int[] sources;
        final int[] destinations;
        int size;
        boolean isLast;

        EdgeBlock() {
            this.sources = new int[BLOCK_SIZE];
            this.destinations = new int[BLOCK_SIZE];
            this.size = 0;
            this.isLast = false;
        }
    }

    public SparseMatrixPipelined(String file) {
        try {
            InputStreamReader is = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader rd = new BufferedReader(is, 32768);
            readFile(rd);
            rd.close();
        } catch (Exception e) {
            System.err.println("Error reading file: " + e);
        }
    }

    private int getNext(BufferedReader rd) throws Exception {
        String line = rd.readLine();
        if (line == null)
            throw new Exception("premature end of file");
        return Integer.parseInt(line);
    }

    private void readFile(BufferedReader rd) throws Exception {
        String line = rd.readLine();
        if (line == null)
            throw new Exception("premature end of file");
        if (!line.equalsIgnoreCase("CSR") && !line.equalsIgnoreCase("CSC-CSR"))
            throw new Exception("file format error -- header");

        num_vertices = getNext(rd);
        num_edges = getNext(rd);

        index = new int[num_vertices + 1];
        destination = new int[num_edges];
        int edgingCounter = 0;

        for (int i = 0; i < num_vertices; ++i) {
            line = rd.readLine();
            if (line == null)
                throw new Exception("premature end of file");
            String elm[] = line.split(" ");
            assert Integer.parseInt(elm[0]) == i : "Error in CSR file";
            index[i] = edgingCounter;
            for (int j = 1; j < elm.length; ++j) {
                int dst = Integer.parseInt(elm[j]);
                destination[edgingCounter++] = dst;
            }
        }
        index[num_vertices] = edgingCounter;
    }

    private class Producer implements Runnable {
        private final BlockingQueue<EdgeBlock> queue;
        private final int startVertex;
        private final int endVertex;
        private final CountDownLatch producerLatch;

        Producer(BlockingQueue<EdgeBlock> queue, int startVertex, int endVertex, CountDownLatch producerLatch) {
            this.queue = queue;
            this.startVertex = startVertex;
            this.endVertex = endVertex;
            this.producerLatch = producerLatch;
        }

        @Override
        public void run() {
            try {
                EdgeBlock block = new EdgeBlock();
                
                for (int i = startVertex; i < endVertex; i++) {
                    final int start = index[i];
                    final int end = index[i + 1];
                    
                    for (int j = start; j < end; j++) {
                        if (block.size >= BLOCK_SIZE) {
                            queue.put(block);
                            block = new EdgeBlock();
                        }
                        
                        block.sources[block.size] = i;  // Source is i in CSR
                        block.destinations[block.size] = destination[j];  // Use destination array
                        block.size++;
                    }
                }

                if (block.size > 0) {
                    block.isLast = (endVertex == num_vertices);
                    queue.put(block);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                producerLatch.countDown();
            }
        }
    }

    // Consumer class remains the same since it just processes edges
    private class Consumer implements Runnable {
        private final BlockingQueue<EdgeBlock> queue;
        private final Relax relax;
        private final CountDownLatch producerLatch;
        private final CountDownLatch consumerLatch;

        Consumer(BlockingQueue<EdgeBlock> queue, Relax relax, CountDownLatch producerLatch, CountDownLatch consumerLatch) {
            this.queue = queue;
            this.relax = relax;
            this.producerLatch = producerLatch;
            this.consumerLatch = consumerLatch;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    EdgeBlock block;
                    try {
                        block = queue.poll(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        break;
                    }

                    if (block == null) {
                        if (producerLatch.getCount() == 0 && queue.isEmpty()) {
                            break;
                        }
                        continue;
                    }

                    for (int i = 0; i < block.size; i++) {
                        relax.relax(block.sources[i], block.destinations[i]);
                    }
                }
            } finally {
                consumerLatch.countDown();
            }
        }
    }

    @Override
    public void calculateOutDegree(int[] outdeg) {
        for (int i = 0; i < num_vertices; i++) {
            outdeg[i] = index[i + 1] - index[i];
        }
    }

    @Override
    public void edgemap(Relax relax) {
        int numThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        BlockingQueue<EdgeBlock> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
        
        int numProducers = Math.max(1, numThreads / 2);
        int numConsumers = Math.max(1, numThreads - numProducers);
        
        CountDownLatch producerLatch = new CountDownLatch(numProducers);
        CountDownLatch consumerLatch = new CountDownLatch(numConsumers);
        
        int verticesPerProducer = Math.max(1, (num_vertices + numProducers - 1) / numProducers);
        
        // Start producers
        for (int i = 0; i < numProducers; i++) {
            int start = i * verticesPerProducer;
            int end = Math.min((i + 1) * verticesPerProducer, num_vertices);
            new Thread(new Producer(queue, start, end, producerLatch)).start();
        }
        
        // Start consumers
        for (int i = 0; i < numConsumers; i++) {
            new Thread(new Consumer(queue, relax, producerLatch, consumerLatch)).start();
        }
        
        try {
            producerLatch.await();
            consumerLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during processing");
        }
    }

    @Override
    public void ranged_edgemap(Relax relax, int from, int to) {

        System.out.println("Testing access");
        // Processing verticess
        for (int i = from; i < to; i++) {
            // check for each vertex
            for (int j = index[i]; j < index[i + 1]; j++) {

                int dst = destination[j];
                relax.relax(i, dst);
            }
        }
    }

    @Override
    public int getNumVertices() {
        return num_vertices;
    }

    @Override
    public int getNumEdges() {
        return num_edges;
    }
}