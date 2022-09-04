## Analyze Conflict

We want to find a possible reason of conflict and add a clause that forbids this assignment. 
To do this we look for a minimum set of literals that reason the conflict in implication graph. 

### Implication graph

Every time we make a decision to set a positive or negative value to a variable,
it causes new appointments to other variables of [unit propagation](propagate.md).

Implication graph is the directed acyclic graph. It's a partial assignment of variables. 
The decision literals don't have ancestors and added to the graph each on its own level. All
literals derived by chosen literals and all previous assignments in the graph are added to 
the current level. 

###Conflict

If there are a pair of opposite literals it's called a conflict. We want to find s cut 
that caused the conflict.

###Algorithm

Put all literals from the conflict clause into a queue. All literals from previous levels
automatically go to the derived clause. Then we take literals from the queue and do the same 
with the clause this literal was derived from. We repeat the action until the size of queue
won't become one and that is first unique implication point (also known as UIP) and all paths
in the graph go throw it. After we've built a clause we do [minimization](minimizing.md).
