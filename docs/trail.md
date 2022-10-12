## Trail

SAT solver stores all decision literals and ones concluded from propagation 
in order they were assigned. Array with this literals is called `trail`. 
Thus, the current partial assignment is directly visible in `trail`.

Main idea of `trail` is to provide the information about literals we 
need to make undefined after `backjump`. Another goal is to store the
`propagation queue`.

More information about trail is [here](https://users.aalto.fi/~tjunttil/2020-DP-AUT/notes-sat/cdcl.html#trails).
In this version `propagation queue` is not stored on the trail, but
almost all modern solvers follow this principal.
