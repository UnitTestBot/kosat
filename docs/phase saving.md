## Value Choosing

Each time we pick a decision variable (by some branching heuristic), we also need to choose its
value. This page is about some efficient strategies of doing it.

### Simple Approach

A simple strategy is to always pick `True` value of a variable (or always `False`). 
Unfortunately, this strategy is not good (e.g. when the only solution
consists of all `False` variables).

Another more effective approach is to pick value randomly. It's a kind
of random, but this method can be rather effective in practice.

### Phase Saving

The idea of this heuristic is to store last assigned value for each variable in a
special array called `polarity`. And when we need to choose a value
of variable, we just take it from this array.


More about **phase saving** [here](http://reasoning.cs.ucla.edu/fetch.php?id=81&type=pdf)
(in 5-th section)
