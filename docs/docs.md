# Brand new SAT-solver using Kotlin

If you don't know any word in documentation, you can try to
find it in [Glossary](definitions.md) with main terms used here.

KoSAT is solving problems given in DIMACS format. If you don't know
how it looks like, you can find it [here](dimacs.md).

### KoSAT heuristics and features

1. [ReNumeration](numeration.md)
2. [Trail](trail.md)
3. [Conflict analysis](analyze.md)
4. [Backjump](backjump.md)
5. [Propagation](propagation.md)
6. [2-watched literals](watched%20literals.md)
7. [VSIDS](branching.md)
8. [Luby restarts](restarts.md)
9. [Phase saving](phase%20saving.md)
10. [ReduceDB based on LBD](reduceDB.md)


https://users.aalto.fi/~tjunttil/2020-DP-AUT/notes-sat/index.html - site with UIP trail
https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.636.4327&rep=rep1&type=pdf - lbd   
https://www.ijcai.org/Proceedings/09/Papers/074.pdf - lbd definition

### About CDCL
Conflict-driven clause learning (CDCL) is an algorithm for 
solving the Boolean satisfiability problem (SAT). It underlies
almost all SAT solvers, so it's important to know its structure.

This is what a simple CDCL algorithm looks like:

```kotlin
while (true) {  
    propagate() // propagation of unit clauses
    if (conflict) {
        // Conflict
        if (topLevelConflict) {
            return Unsatisfiable
        }
        analyze(conflictClause) // analyze conflict to produce new clause
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
As you can see, the algorithm's main idea is to make partial
assignments and learn information if something goes wrong.
So if we get a conflict, we try to analyze it and learn new 
clause, based on this conflict. If all variables are assigned,
and there is no conflict, this means we have found a solution.

### Code structure

#### Storing data

All variables are stored in the `vars` array. It means they have
some useful characteristics:
- value (TRUE, FALSE, UNDEFINED)
- reason (clause it was concluded from, if it's not a decision variable)
- level (decision level when variable was assigned)

Trail is used for decision literals and propagations. You can find
more information about it [here](trail.md).

Clauses are stored in 2 arrays: constraints and learnts.
- `Constraints` array is used for clauses given in initial problem and doesn't
change during solution.
- `Learnts` array is used for clauses learned during the process
of partial assignment. This array can be modified many times, and
sometimes its size can be rather big to prove problem has no solution.

#### More about basics of CDCL

Let's go through all functions mentioned in simple implementation:

1. [propagate()](propagation.md) — This function is important for
   making decisions based on current partial assignment. In details,
   it adds literals on trail, i.e. values of variables which follows
   from assignment
2. If `propagate()` got a conflict, we check whether `level` equals to 0 - 
   this means that conflict follows from initial task, i.e. problem is `UNSAT`.
   Otherwise, we run [analyze()](analyze.md) function with a 
   conflict clause to construct a new clause (called lemma). 
   
3. [backjump()](backjump.md) is used to return to `level` where lemma
   would be unit clause (it's possible because we've constructed lemma
   in this way).
4. 
   If there is no conflict, we need to check if all variables are assigned.
   In this case, problem is `SAT`, and we have a solution. Otherwise, 
   we continue making decisions. That's why we need 
   [pickNewBranchingLiteral()](branching.md).

