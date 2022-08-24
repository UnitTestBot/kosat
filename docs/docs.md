# Brand new SAT-solver using Kotlin

If you don't know some word in the documentation there is a chance its definition exists
in [definition.md](definitions.md) file.

TODO: Dimacs format

Solver using:
1. ReNumeration  -- Polina TODO
2. Preprocessing?
3. Trail  -- room for improvements
4. Analyze conflict
8. UIP vertex
5. Backjump
6. Propagate
7. 2 Watched literals
9. VSIDS
10. Restarts (Luby) -- done
11. Phase saving -- done
12. ReduceDB + LBD -- done
13. Work with assumptions


https://users.aalto.fi/~tjunttil/2020-DP-AUT/notes-sat/index.html - site with UIP trail and so on  
http://minisat.se/downloads/MiniSat.pdf - minisat structure  
https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.636.4327&rep=rep1&type=pdf - lbd  
http://reasoning.cs.ucla.edu/fetch.php?id=81&type=pdf - phase saving  
https://www.ijcai.org/Proceedings/09/Papers/074.pdf - lbd definition


The algorithm without hard details looks like this:
```kotlin
while (true) {  
    propagate() // propagation of unit clauses
    if (conflict) {
        // Conflict
        if (topLevelConflict) {
            return Unsatisfiable
        }
        analyze() // analyze conflict to produce new clause
        backjump() // undo assignments until new clause is unit
    } else {
        // No conflict
        if (allVariablesAssigned) {
            return Satisfiable
        }
        pickNewBranchingLiteral()
    }
}

```
So we're just trying to do some partial assignment, if we get conflict we learn a new clause
that will not allow us this partial assignment. And if we assigned all variables then the answer
to the problem is SAT.

We store our decision literals and literals concluded by propagation on the [trail](trail.md). Also, we store
for every variable in the `vars` array: value (TRUE, FALSE, UNDEFINED), reason (clause it was concluded from, decision
variables had null), level (decision level where it was concluded or decision variable which corresponds to
this level).

Our decisions increase `level` by 1.

Let's go through all functions mentioned in this simple realization:
1. [propagation()](propagate.md) -- The Goal of propagation is to make all possible conclusions
   of partial assignment that we've got. And find a conflict clause (clause where all literals
   are 'false') if there is one.
2. If we've got conflict we check whether `level` equals 0, if so then there is conflict concluded from
   initial clauses. Another way we [analyze()](analyze.md) conflict clause and reasons of variables to
   build new clause (called lemma). And then [backjump()](backjump.md) to `level` where lemma
   would be unit clause (this is possible because we constructed lemma this way).
3. If there is no conflict firstly we check whether there are still unassigned variables if there are no
   then we just constructed a solution and the problem is SAT. Another way we want to choose a new decision
   variable and value for it. There are some different approaches to do so, they are considered in
   detail in [branching.md](branching.md).

Now we're going to consider advanced heuristics used in our algorithm:
1. [Restarts](restarts.md)
2. [ReduceDB](reduceDB.md)
3. [Minimizing lemma](minimizing.md)
