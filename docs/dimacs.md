##DIMACS

We use common format for SATs - DIMACS.

This is an example of comment line.

```
c comment line
```

CNF starts with headline ```p cnf [number of variables] [number of clauses]```.

Next ```[number of clauses]``` lines consist of clauses where variables are indexed
from one and could be positive or negative (it means not). Clause input ends with 0.

```
p cnf 3 2
1 -2 0
2 3 0
```

[Learn more](http://www.satcompetition.org/2004/format-solvers2004.html)
