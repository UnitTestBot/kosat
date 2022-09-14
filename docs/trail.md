## Trail

We store all decision literals and ones concluded from propagation in order they
were obtained. Array that stores they called **trail**. Thus, the current partial
truth assignment is directly visible in the trail.

Main goal of the trail is to provide us with information which literals need to 
become undefined after backjump. Second most important objective is to store 
propagation queue.

TODO: propagation queue

[More info about trail](https://users.aalto.fi/~tjunttil/2020-DP-AUT/notes-sat/cdcl.html#trails), 
it is version without storing propagation queue on the trail. But almost all 
modern SAT-solvers store literals to propagate right on the trail.
