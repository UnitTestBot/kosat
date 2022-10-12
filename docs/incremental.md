## Incremental

Incremental solving means that after solving the initial problem, it can be solved
with some additional modifications. Problem can be solved with new clauses
or with new assumptions (partial assignment of variables).

So there are 2 main things you should know about incremental solving:
- Incremental solver maintains adding clauses after it's already been solved.
- Incremental solver supports to run it with assumptions

> **Note:** The solver resets passed assumptions on each `solve()` call,
> so you can consider the assumptions as 'one-time unit clauses'