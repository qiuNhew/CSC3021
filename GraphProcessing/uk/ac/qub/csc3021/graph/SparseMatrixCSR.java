package uk.ac.qub.csc3021.graph;

import java.io.*;
import java.util.concurrent.*;

public class SparseMatrixCSR extends SparseMatrix {
    private static final int BUFFER_SIZE = 4096;
    
    int[] index;
    int[] destination;
    int num_vertices;
    int num_edges;

    // Data structure for passing raw lines
    private static class RawLine {
        String content;
        int lineNumber;
        boolean isLast;

        RawLine(String content, int lineNumber, boolean isLast) {
            this.content = content;
            this.lineNumber = lineNumber;
            this.isLast = isLast;
        }
    }

    // Data structure for passing parsed data
    private static class ParsedLine {
        int[] destinations;
        int lineNumber;
        boolean isLast;

        ParsedLine(int[] destinations, int lineNumber, boolean isLast) {
            this.destinations = destinations;
            this.lineNumber = lineNumber;
            this.isLast = isLast;
        }
    }

    // Reader thread
    private class LineReader implements Runnable {
        private final BufferedReader reader;
        private final BlockingQueue<RawLine> outputQueue;
        private final int startLine;
        private final int endLine;

        LineReader(BufferedReader reader, BlockingQueue<RawLine> outputQueue, 
                  int startLine, int endLine) {
            this.reader = reader;
            this.outputQueue = outputQueue;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        @Override
        public void run() {
            try {
                for (int i = startLine; i < endLine; i++) {
                    String line = reader.readLine();
                    if (line == null) throw new Exception("Premature end of file");
                    outputQueue.put(new RawLine(line, i, i == endLine - 1));
                }
            } catch (Exception e) {
                System.err.println("Error in reader: " + e);
            }
        }
    }

    // Parser thread
    private class LineParser implements Runnable {
        private final BlockingQueue<RawLine> inputQueue;
        private final BlockingQueue<ParsedLine> outputQueue;
        private volatile boolean done = false;

        LineParser(BlockingQueue<RawLine> inputQueue, BlockingQueue<ParsedLine> outputQueue) {
            this.inputQueue = inputQueue;
            this.outputQueue = outputQueue;
        }

        @Override
        public void run() {
            try {
                while (!done) {
                    RawLine rawLine = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (rawLine == null) {
                        if (inputQueue.isEmpty()) break;
                        continue;
                    }

                    String[] elements = rawLine.content.split(" ");
                    int[] destinations = new int[elements.length - 1];
                    for (int i = 1; i < elements.length; i++) {
                        destinations[i - 1] = Integer.parseInt(elements[i]);
                    }
                    
                    outputQueue.put(new ParsedLine(destinations, rawLine.lineNumber, rawLine.isLast));
                    if (rawLine.isLast) done = true;
                }
            } catch (Exception e) {
                System.err.println("Error in parser: " + e);
            }
        }
    }

    public SparseMatrixCSR(String file) {
        try {
            long startTime = System.nanoTime();
            
            InputStreamReader is = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader rd = new BufferedReader(is);
            readFile(rd);
            rd.close();
            
            double totalTime = (System.nanoTime() - startTime) * 1e-9;
            System.err.println("Total file reading time: " + totalTime + " seconds");
        } catch (Exception e) {
            System.err.println("Error reading file: " + e);
        }
    }

    void readFile(BufferedReader rd) throws Exception {
        // Read header and dimensions
        String line = rd.readLine();
        if (line == null || (!line.equalsIgnoreCase("CSR") && !line.equalsIgnoreCase("CSC-CSR")))
            throw new Exception("file format error -- header");

        num_vertices = getNext(rd);
        num_edges = getNext(rd);

        // Allocate arrays
        index = new int[num_vertices + 1];
        destination = new int[num_edges];

        // Create queues for pipeline stages
        BlockingQueue<RawLine> rawLines = new ArrayBlockingQueue<>(BUFFER_SIZE);
        BlockingQueue<ParsedLine> parsedLines = new ArrayBlockingQueue<>(BUFFER_SIZE);

        // Start timing
        long pipelineStart = System.nanoTime();

        // Create and start threads
        Thread readerThread = new Thread(new LineReader(rd, rawLines, 0, num_vertices));
        Thread parserThread = new Thread(new LineParser(rawLines, parsedLines));

        readerThread.start();
        parserThread.start();

        // Process parsed lines and build the graph
        int edgingCounter = 0;
        int linesProcessed = 0;

        while (linesProcessed < num_vertices) {
            ParsedLine parsedLine = parsedLines.poll(100, TimeUnit.MILLISECONDS);
            if (parsedLine == null) continue;

            index[parsedLine.lineNumber] = edgingCounter;
            for (int dst : parsedLine.destinations) {
                destination[edgingCounter++] = dst;
            }
            linesProcessed++;
        }

        index[num_vertices] = edgingCounter;

        // Record timing
        double pipelineTime = (System.nanoTime() - pipelineStart) * 1e-9;
        System.err.println("Pipeline processing time: " + pipelineTime + " seconds");
        System.err.println("Lines processed per second: " + (num_vertices / pipelineTime));
    }

    int getNext(BufferedReader rd) throws Exception {
        String line = rd.readLine();
        if (line == null)
            throw new Exception("premature end of file");
        return Integer.parseInt(line);
    }

    // Rest of the methods remain unchanged
    @Override
    public int getNumVertices() {
        return num_vertices;
    }

    @Override
    public int getNumEdges() {
        return num_edges;
    }

    @Override
    public void calculateOutDegree(int[] outdeg) {
        for (int i = 0; i < num_vertices; i++) {
            outdeg[i] = index[i + 1] - index[i];
        }
    }

    @Override
    public void edgemap(Relax relax) {
        System.out.println("Testing access");
        for (int i = 0; i < num_vertices; i++) {

            for (int j = index[i]; j < index[i + 1]; j++) {

                int dst = destination[j];
                relax.relax(i, dst);
            }
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
}