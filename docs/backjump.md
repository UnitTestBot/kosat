## Backjump

We learn new clauses when we get conflicts. In this state given
assignments of variables are incorrect as they create a conflict.
In order to fix that we need to make `backjump`. This means, we just
need to reduce current level. 

### Changing level

We need to choose a `level` such that added clause becomes a unit.
There are 2 different cases:
- Clause of size 1

As we mentioned in [docs](docs.md), we store these clauses as a trail
0-level decisions. So in this case, `level` should be set to 0, 
and we need to clear trail.

- Clause of size 2 or more:

As we add literals to lemma according to their assignment level,
second maximum level of literals in clause gives us the result.
It corresponds literal with index `1` in clause. So we make
`level` equal to this variable's level and then clear trail.