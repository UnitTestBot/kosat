##Incremental

1) Incremental solver maintains adding clauses after it's already been solved.
2) Incremental solver lets to run it with assumptions - partial assignment of variables. Solver checks
is it possible to find an answer with current values or not.
Every run doesn't remember previous assumptions, as we run it with the set of unit clauses.
