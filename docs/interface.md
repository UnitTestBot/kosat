## Solver's interface functions

All variables are numbered in DIMACS format.

Add a variable to CNF:
- `newVariable()`

Add a clause to CNF as pure literals or list of literals:
- `newClause(literals: List<Int>)`

Solve CNF without assumptions:
- `solve(): Boolean`

Solve CNF with the passed assumptions:
- `solve(assumptions: List<Int>): Boolean`

Query boolean value of a literal:
- `value(lit: Int): Boolean`

**Note:** the solver should be in the SAT state.

Query the satisfying assignment (model) for the SAT problem:
- `fun getModel(): List<Int>`

**Note:** the solver should be in the SAT state.
