## 2-Watched literals

As you already know, `propagation` takes most time of the solving process.
In order to reduce the time, we use 2-watched literals heuristic.
> **Note:** it's one of the most useful and important heuristics for a good solver.

The idea is to keep 2 pointers for each clause. These pointers "watch"
on unassigned literals for clause. This helps us find unit clauses.
If assigned literal is not watching clause then this clause can't become
unit as it has 2 unassigned literals left. Otherwise, we go through all literals
in clause and replace chosen literal with unassigned. If there are no new
unassigned literals - clause becomes unit. So we don't waste time on
watching all clauses and checking if the become unit. So we strongly 
reduce the time of the longest solver's function. That's why this
gives good improvement for the solving process.

We've taken implementation used in MiniSat as it's pretty effective and hasn't
changed a lot since that time.

You can find more about it [here](http://fmv.jku.at/fleury/papers/Fleury-thesis.pdf) (Chapter 5)

