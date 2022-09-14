## Propagation

> It's main function in solving process. It usually takes
> **70-80%** of all solver's working time!

This function tries to find clauses with all `False` literals
except exactly one, which is `Undefined`. If such clause is found,
we can surely say that `Undefined` literal must be `True`. However,
after understanding it, assigned literal can create new unit clauses.
Let's see how it works.

**Example:** 

Suggest `x1` and `x2` are `False`, then we may conclude `x3` is `False`.
Looking at the second clause, we understand that x4 is `True` and then
third clause gives us the contradiction
```
1 2 -3 0
3 4 0
2 4 5 0
```

In fact, propagation is not as simple as you could think at first.
Main reason is that it's not easy to understand if literal's assignment
generates new clauses for propagation (if we go through all clauses,
containing chosen literal, it can be very long). To solve this problem
**all** state-of-the-art solvers use [2-watched literals](watched literals.md) heuristic.
