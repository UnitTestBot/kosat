## Numbering of literals

Solver accepts requests in [DIMACS](dimacs.md) format, but it's not the most convenient way
to work with literals and variables by some reasons:
- negative indices of literals
- numbering from 1

That's why we decided to renumber input, given in classical [DIMACS](dimacs.md) format.

> **Note:** we talk here about internal numeration, which is different from
DIMACS.

Our current numbering is quite simple:
1. Variable's indexation starts from zero 
2. Index of positive literal is doubled index of variable `2 * v`
3. Index of negative literal is doubled index of variable plus one `2 * v + 1`

vice versa by `xor 1`. So there is no need to know current sign of a literal.
