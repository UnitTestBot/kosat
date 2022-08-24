## Restarts

SAT-solver may get stuck in some branch of the search tree
(for example, choosing that x1 is true and there are only solutions where x1 is false).

To prevent this we used restarts.

We want to do a restart after several conflicts. Next techniques differ in the way we
choose this number.

### Simple Restarts Scheme

We will restart after restartNumber (50 by default) of conflicts. After every restart, we multiply restartNumber by restartInc (1.1 by default).

Not a very good approach because restartNumber gets gigantic too fast (sometimes we want more rapid restarts).

### Luby Restarts Scheme

There is a technique called Luby sequence restarts. It's used for Las Vegas algorithms.
https://www.cs.utexas.edu/~diz/pubs/speedup.pdf

The idea of this technique is to use the Luby sequence:  
1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8 ...

This sequence is constructed like this:  
We start with a sequence with 1 element -- $$2^0$$.
For every step we take the finite sequence that we made on the previous step, double it up and add new power of 2.
So, the first iterations of this algorithm are going to be:  
1  
1, 1, 2  
1, 1, 2, 1, 1, 2, 4  
1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8  
1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, 16

Back to restarts, after ith restart, we want to learn u * luby[i] conflicts before the next restart, where u is Luby restart constant (by default 50), and luby[i] is ith element of Luby sequence.
