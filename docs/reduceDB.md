## ReduceDB

Solver stores initial clauses in the array called constraints (clauses added by
newClause will be stored there as well). Lemmas learnt by analyzing conflicts are 
stored in array called learnts.  
Solver usually learn new clauses really fast, but most of them are useless for 
future conflicts, they just occupy a space and make propagation much longer.  

To get rid of this effect we reduce number of learnts in array once in a while.  
The strategy is to remove half of learnts when their number becomes equal to 
reduceNumber (6000 by default), after reducing reducedNumber increases by 
reduceIncrement (500 by default).  

But we need to choose which learned clauses to remove. There is some metrics 
to understand by clause good it or not. There are 2 main ones: LBD, VSIDS like metric.


### LBD

[Justification of LBD](https://www.ijcai.org/Proceedings/09/Papers/074.pdf),

*Definition*. Given a clause C, and a partition of its literals into n subsets 
according to the current assignment, s.t. literals are partitioned
w.r.t their decision level. The LBD of C is exactly n.

Clause with small lbd give us fast propagation and therefore faster assignment.

### VSIDS

TODO: Danya?
