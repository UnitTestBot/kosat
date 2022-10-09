## Polarity choice

Each time we pick a decision variable (by some branching heuristic), we also need to choose its
polarity. This page is about some efficient strategies of doing it.

### Simple Approach

A simple strategy is to always pick `True` value of a variable (or always `False`). 
However, on practice this strategy is not as effective as advanced ones.

Another more effective approach is to pick polarity randomly. It's a kind
of random, but this method can be rather effective in practice.

### Phase Saving

The idea of this heuristic is to store last assigned polarity for each variable in a
corresponding array called `polarity`. And when we need to choose it, 
we just take value from the array.


More about `phase saving` can be found [here](http://reasoning.cs.ucla.edu/fetch.php?id=81&type=pdf)
(in the 5-th section)
