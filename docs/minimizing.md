## Minimizing Clause

As it was mentioned in [Conflict analysis](analyze.md), we want to find a clause
with minimal number of variables to avoid the conflict with current assignment.

Restored clause contains the UIP of the last level 
and some set of literals withdrawn by previous decisions. 
We build clause during our algorithm and take all literals from the previous layer.
Nevertheless, if clause contains literals, that can be withdrawn by other 
literals in this clause, we obviously don't need to keep them. 

To reduce the size of clause, we mark all chosen literals and delete the ones 
with all marked ancestors in an implication graph. 
