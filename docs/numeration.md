## Numeration

Solver accepts requests in [DIMACS](dimacs.md) format, but it's not the most convenient way
to work with literals and variables by such reasons as negative indexes of literals and numbering from first index. 
That's why we decided to renumber input in classical [DIMACS](dimacs.md) format into our own for simplifying code base.

Current indexation is quite simple:
1) Variable's indexation starts from zero
2) Index of positive literal is doubled index of variable
3) Index of negative literal is doubled index of variable plus one

Thus, positive literal can be derived from negative and 
vice versa by `xor 1`. There is no need to know current sign of literal.
