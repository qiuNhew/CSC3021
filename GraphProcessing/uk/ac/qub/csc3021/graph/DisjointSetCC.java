package uk.ac.qub.csc3021.graph;

import java.util.concurrent.atomic.AtomicIntegerArray;

public class DisjointSetCC {
    private static class DSCCRelax implements Relax {
        private final AtomicIntegerArray parent;
        private final int[] degree;  // Store degree for each vertex
        
        DSCCRelax(AtomicIntegerArray parent_, int[] degree_) {
            parent = parent_;
            degree = degree_;
        }

        public void relax(int src, int dst) {
            union(src, dst);
        }

        public int find(int x) {
            int root = x;
            // Find root
            while (root != parent.get(root)) {
                root = parent.get(root);
            }
            
            // Path compression
            while (x != root) {
                int next = parent.get(x);
                parent.compareAndSet(x, next, root);
                x = next;
            }
            
            return root;
        }
        
        private boolean sameSet(int x, int y) {
            return find(x) == find(y);
        }

        private boolean union(int x, int y) {
            while (true) {
                int rootX = find(x);
                int rootY = find(y);

                if (rootX == rootY) {
                    return false;  // Already in same set
                }

                // Use degree for linking order
                // Higher degree nodes become roots
                if (degree[rootX] > degree[rootY] || 
                   (degree[rootX] == degree[rootY] && rootX < rootY)) {
                    // Try to point rootY to rootX
                    if (parent.compareAndSet(rootY, rootY, rootX)) {
                        return true;
                    }
                } else {
                    // Try to point rootX to rootY
                    if (parent.compareAndSet(rootX, rootX, rootY)) {
                        return true;
                    }
                }
                
            }
        }
    }

    public static int[] compute(SparseMatrix matrix) {
        long tm_start = System.nanoTime();

        final int n = matrix.getNumVertices();
        final AtomicIntegerArray parent = new AtomicIntegerArray(n);
        final boolean verbose = true;

        // Calculate vertex degrees for link order
        int[] degree = new int[n];
        matrix.calculateOutDegree(degree);

        // Initialize disjoint sets
        for (int i = 0; i < n; ++i) {
            parent.set(i, i);
        }

        DSCCRelax DSCCrelax = new DSCCRelax(parent, degree);

        double tm_init = (double)(System.nanoTime() - tm_start) * 1e-9;
        System.err.println("Initialisation: " + tm_init + " seconds");
        tm_start = System.nanoTime();

        ParallelContext context = ParallelContextHolder.get();

        // Process edges
        context.edgemap(matrix, DSCCrelax);

        double tm_step = (double)(System.nanoTime() - tm_start) * 1e-9;
        if (verbose)
            System.err.println("processing time=" + tm_step + " seconds");
        tm_start = System.nanoTime();

        // Post-process: Map to continuous component IDs
        int ncc = 0;
        int[] remap = new int[n];
        for (int i = 0; i < n; ++i) {
            if (DSCCrelax.find(i) == i) {
                remap[i] = ncc++;
            }
        }

        if (verbose)
            System.err.println("Number of components: " + ncc);

        // Calculate component sizes
        int[] sizes = new int[ncc];
        for (int i = 0; i < n; ++i) {
            ++sizes[remap[DSCCrelax.find(i)]];
        }

        if (verbose)
            System.err.println("DisjointSetCC: " + ncc + " components");

        return sizes;
    }
}