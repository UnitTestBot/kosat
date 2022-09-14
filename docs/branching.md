## Branching heuristics

Main goal is to decide which variable is going to be next decision variable. 
There are some basic approaches: simple, VSIDS, MTF.

### Simple

We could just pick first undefined variable. But we don't use any information about
problem, so it's not fast implementation, but easy to understand or code.

### VSIDS

#### How VSIDS work?

We start an array called `activity` which stores activity of each 
variable. VSIDS just takes first undefined variable with the 
highest activity. It's implemented by heap structure. We keep 
heap of potentially undefined variables. When it's time to choose 
variable we just get it from top of the heap. But sometimes this 
variable can be already defined (it happens because of propagation). 
In this case we continue the process of removing variables from 
top of the heap until we get undefined one. This is the choice.
Heap is also updated when we clear trail. This happens because we
undefine variables - so we need to be sure they would be stored
in our heap.

#### How we change activities?

In our implementation we bump all variables in a conflict clause
we get by analyze. The `activity` array should be updated, and 
also we need to update heap. For this operation there is a special
index array - for each variable it contains index of its position
in heap array. So bumping activity works as a `siftUp` operation
for heap. Variables are bumped by `activityInc`

After some number of conflicts (decay) we reduce activity of all
variables by half. But in big CNF's it's a long operation so instead
of doing it we double value of `activityInc`. Of course, we can't
double up `activityInc` too much. So when it becomes more than
`incLimit` we honestly divide all activities by `incLimit` and
set value of `activityInc` to 1.

links (???)
