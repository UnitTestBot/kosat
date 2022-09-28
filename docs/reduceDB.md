## ReduceDB

Solver stores initial clauses in the array called `constraints`. Lemmas
learnt by `conflict analyze` are stored in array called `learnts`.  
Solver usually learns new clauses really fast, but most of them are
useless for future conflicts, so they just take up extra memory and
make `propagation` work longer.  

To get rid of this effect, we reduce size of `learnts` array once in a while.  
The strategy is to remove half of learnt clauses when size of array
becomes equal to `reduceNumber`. After operation `reducedNumber`
is increased by`reduceIncrement`.  

But we need to choose clauses to remove. There are some metrics 
to understand if clause is good or not. There are 2 main:
`LBD` or `VSIDS-like metric`. We use `LBD`.


### LBD (Literal Block Distance)

**Definition:** Given a clause, and a partition of its literals into
`n` subsets according to the current assignment, s.t. literals are
partitioned w.r.t their decision level. The `LBD` of clause is exactly `n`.

Clause with small `LBD` give us fast propagation and therefore 
faster assignment.

[Justification of LBD](https://www.ijcai.org/Proceedings/09/Papers/074.pdf)