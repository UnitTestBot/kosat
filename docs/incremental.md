## Incremental

1. Incremental solver maintains adding clauses after it's already been solved.
2. Incremental solver supports to run it with assumptions (partial assignment of variables). 

> **Note:** Solver checks if it's possible to find an answer with 
current assignments or not. Each launch doesn't remember previous 
assumptions, as we run it with the **new** set of unit clauses.
