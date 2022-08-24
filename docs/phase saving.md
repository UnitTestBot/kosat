## Value Choosing

Every time we pick a decision variable (by some branching heuristic) we must choose its
value too. This problem is solved in this section.

### Simple Approach

A simple strategy is to always pick the 'true' value (or always 'false'). There is a paper that
states picking 'false' is much better, but I've lost it. Another simple approach is to pick value randomly.  
These methods don't use any information about a problem, so there is room for improvement.

### Phase Saving

If cnf may be split into sub-problems our algorithm would interfere with itself, because
when it starts to solve the next sub-problem backjump would throw us to the decision level of
previous sub-problem, and we will be forced to solve it once again.

TODO: it helps with branching too.

To prevent this we used phase saving.  
[Good paper on the topic](http://reasoning.cs.ucla.edu/fetch.php?id=81&type=pdf)
(the solution to the problem is in the 5th section)

Idea is to store the last value of the variable obtained by
the decision or from propagation in the array called polarity. Next time we want this
variable to be a decision we choose its value from polarity.
