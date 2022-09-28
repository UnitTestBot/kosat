## Incremental

Incremental solving means that after solving the problem it can be solved
with some additional modifications. This can help if you are working with
some dynamically changing structures as the solver is not starting the solving
process from the beginning. You can add some partial assignments (i.e. ask to solve 
the problem if you know some values of variables)

So there are 2 main things you should know about incremental solving:
- Incremental solver maintains adding clauses after it's already been solved.
- Incremental solver supports to run it with assumptions (partial assignment of variables). 

> **Note:** Solver checks if it's possible to find an answer with 
current assignments or not. Each launch doesn't remember previous 
assumptions, as we run it with the **new** set of unit clauses.
