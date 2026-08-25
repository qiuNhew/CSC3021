# CSC3021 — Concurrent Programming

Coursework for **CSC3021 Concurrent Programming** (Queen's University Belfast, 2023/24).

This repository contains Assignment 3: **[GraphProcessing](GraphProcessing/)** — parallel
graph algorithms in Java over large sparse-matrix graph representations.

## What's here

Two graph algorithms run over a graph loaded into one of several sparse-matrix layouts:

| Algorithm | Entry point | Output |
|---|---|---|
| PageRank (`pr`) | [PageRank.java](GraphProcessing/uk/ac/qub/csc3021/graph/PageRank.java) | PageRank value per vertex |
| Connected components, label propagation (`cc`) | [ConnectedComponents.java](GraphProcessing/uk/ac/qub/csc3021/graph/ConnectedComponents.java) | histogram of cluster sizes |
| Connected components, disjoint-set / union-find (`ds` or `opt`) | [DisjointSetCC.java](GraphProcessing/uk/ac/qub/csc3021/graph/DisjointSetCC.java) | histogram of cluster sizes |

Graph storage formats, selected at runtime by the `format` argument:

| Format | Class | Notes |
|---|---|---|
| `COO` | [SparseMatrixCOO](GraphProcessing/uk/ac/qub/csc3021/graph/SparseMatrixCOO.java) | coordinate list |
| `CSR` | [SparseMatrixCSR](GraphProcessing/uk/ac/qub/csc3021/graph/SparseMatrixCSR.java) | compressed sparse rows (out-edges) |
| `CSC` | [SparseMatrixCSC](GraphProcessing/uk/ac/qub/csc3021/graph/SparseMatrixCSC.java) | compressed sparse columns (in-edges) |
| `ICHOOSE` | [SparseMatrixPipelined](GraphProcessing/uk/ac/qub/csc3021/graph/SparseMatrixPipelined.java) | CSR loaded through a producer/consumer pipeline |

Parallelism is abstracted behind `ParallelContext`:

- `ParallelContextSingleThread` — baseline, one thread.
- `ParallelContextSimple` — partitions the vertex range across `num_threads` threads,
  each calling `ranged_edgemap` over its own slice.
- `ParallelContextThread` — used by `ICHOOSE`; the parallelism is in the *file loading*
  (`SparseMatrixPipelined`), while `edgemap` itself runs on one thread.

## Building

```
cd GraphProcessing
make
```

Or, without make:

```
cd GraphProcessing
javac -d . -cp . uk/ac/qub/csc3021/graph/*.java
javac -cp . Driver.java
```

## Running

```
java -ea -cp . Driver <algorithm> <num-threads> <outputfile> <format> <inputfiles...>
```

- `algorithm` — `pr`, `cc`, `ds`, or `opt`
- `format` — `COO`, `CSR`, `CSC`, or `ICHOOSE`
- `inputfiles` — one or more graph files; the format is inferred from the file
  extension (`.coo`, `.csr`, `.csc`, or `.csc-csr` for a combined file)
- `-ea` enables the assertions the code uses as correctness checks (Java disables them
  by default)

Example:

```
java -ea -cp . Driver cc 4 outputfile.txt CSR graph.csc-csr
```

## Graph data

The data sets are **not** in this repository — they are multi-gigabyte and are
downloaded separately from
<http://www.eeecs.qub.ac.uk/~H.Vandierendonck/CSC3021/graphs/>. Decompress before use.

File formats:

- **CSR / CSC** — a header line with `<num-vertices> <num-edges>`, then one line per
  vertex: the vertex ID followed by the IDs of its neighbours (out-edges for CSR,
  in-edges for CSC), space separated.
- **COO** — a header line with `<num-vertices> <num-edges>`, then one line per edge:
  source ID and destination ID, space separated.

## Attribution

The `Driver`, algorithm implementations, `Relax`/`ParallelContext` scaffolding, the
Makefile, and [GraphProcessing/README.md](GraphProcessing/README.md) are skeleton code
provided by the module organiser (Prof. Hans Vandierendonck, QUB). The sparse-matrix
data structures, the pipelined loader, and the parallel contexts are the coursework
implementation.
