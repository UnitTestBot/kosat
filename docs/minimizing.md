## Minimizing Clause

As it was mentioned in [Analyze Conflict](analyze.md) we want to find a clause
with minimum number of variables to avoid the conflict of current assignment.

Restored clause contains the UIP of the last level 
and some set of literals withdrawn by previous decisions. 
We build the clause during our algorithm and take every previous layer literal.
Nevertheless, if clause contains literals, that can be withdrawn by other 
literals in this clause we obviously don't need them. 

To reduce the size of clause we mark all chosen literals and delete the ones 
with all marked ancestors in an implication graph. 
