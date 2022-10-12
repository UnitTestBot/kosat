## Restarts

SAT-solver may get stuck in some branch of the search tree
(e.g., choosing true value of variable `x1` as there are only 
solutions where `x1`is false).

To prevent this we use restarts.

We want to make restarts after several number of conflicts. Techniques
described below, differ only in the way we choose this number.

### Simple Restarts Scheme

We make restart after some number of conflicts. This number is 
stored in `restartNumber` variable. After each restart,
we multiply variable by `restartInc`. So this strategy is
increasing the iteration step to find a solution or a new one
conflict in the branch and not to get stuck in this branch for a long time.

However, it's not a very good approach as `restartNumber` variable
grows too fast (sometimes we want more rapid restarts).

### Luby Restarts Scheme

There is a technique called Luby sequence restarts. It's used in Las Vegas algorithms.
https://www.cs.utexas.edu/~diz/pubs/speedup.pdf

The idea of this technique is to use the Luby sequence:
```
1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8 ...
```

This sequence is constructed by a simple algorithm:  
We start a sequence with `1`.
On each step we take finite sequence, built on previous step,
double it up and add next `power of 2` in the end.

Here you can see how it works:
```
1  
1, 1, 2  
1, 1, 2, 1, 1, 2, 4  
1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8  
1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, 16
```

Back to restarts, after `i-th` of them, we want to learn `u * luby[i]`
conflicts before the next restart, where `u` is Luby restart 
constant, and `luby[i]` is `i-th` element of luby sequence.
