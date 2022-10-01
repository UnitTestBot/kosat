## Solver's interface functions

Add a variable to CNF and return its number:
- `addVariable(): Int`

Add a clause to CNF as pure literals or list of literals:
- `addClause(lit: Lit)`
- `addClause(lit1: Lit, lit2: Lit)`
- `addClause(lit1: Lit, lit2: Lit, lit3: Lit)`
- `addClause(literals: List<Lit>)`
- `addClause(literals: Iterable<Lit>)`

Solve CNF without assumptions:
- `solve(): Boolean`

Solve CNF with the passed assumptions:
- `solve(assumptions: List<Lit>): Boolean`
- `solve(assumptions: Iterable<Lit>): Boolean`

Query boolean value of a literal:
- `getValue(lit: Lit): Boolean`

**Note:** the solver should be in the SAT state.

Query the satisfying assignment (model) for the SAT problem:
- `fun getModel(): List<Lit>`

**Note:** the solver should be in the SAT state.
