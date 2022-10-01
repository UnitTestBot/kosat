## Branching heuristics

Main goal of these heuristics is to decide which variable is better
for being next decision variable. There are some basic methods: 
first undefined, VSIDS, MTF.

### First undefined

We can choose first undefined variable. It is clear that in this case
we don't use any information about results we got before. So this method
is not quite effective as it's not flexible to changes we get during
the solving process.

### VSIDS

#### How VSIDS work?

We keep an array called `activity` which stores activity of each 
variable. VSIDS just takes first undefined variable with the 
highest activity. It's implemented by heap structure. We keep 
heap of potentially undefined variables. When it's time to choose 
variable we just get it from the top of the heap. But sometimes this 
variable can be already defined (it happens because of propagation). 
In this case we continue the process of removing variables from the
top of the heap until we get undefined one. This will be our choice.
Besides, heap should be updated when we clear trail. This happens 
because we undefine variables - so we need to be sure they would 
be stored in our heap (i.e. if it's removed from heap - then we should add it).

#### How we change activities?

In our implementation we bump all variables in a conflict clause
we get by analyze. The `activity` array should be updated, and 
also we need to update heap. For this operation there is a special
index array - for each variable it contains index of its position
in heap array. So bumping activity works as a `siftUp` operation
for heap. Variables are bumped by `activityInc` and this variable
is multiplied by `multiplier` after each conflict.

If the biggest activity value is more than `activityLimit`, we divide
all activities by `activityLimit` in order to avoid overflow.
