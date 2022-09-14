# What is KoSAT?
####![Windows](https://github.com/UnitTestBot/kosat/actions/workflows/build-windows.yml/badge.svg) ![Linux](https://github.com/UnitTestBot/kosat/actions/workflows/build-linux.yml/badge.svg)
KoSAT is **pure Kotlin** CDCL SAT solver based on MiniSat core.
It is solving boolean satisfiability problems given in DIMACS format.
Solver supports incremental solving.

## How to use KoSAT?
There are many ways to use our solver:
<details>
  <summary>On site</summary>

In the picture below you can see site dialog window. 
All you need is to enter the problem in DIMACS format and click on the
***CHECK SAT*** button.

![img](assets/site.png)

The site is available at the link below:

> http://www.utbot.org/kosat/
____________
</details>
<details>
  <summary>By Javascript</summary>

1. Download kosat.js file from releases.
2. Import this file in your. For example:  
`const kosat = require("./kosat.js");`
3. Use Kosat interface (described in [incremental](docs/incremental.md) documentation).
____________
</details>
<details>
  <summary>By Java/Kotlin</summary>

You can use KoSAT directly from Kotlin. As always, problem
should be given in DIMACS format. There are 2 options
how to enter your CNF: 
1. Console input
2. Write to `input.txt` located in `src\jvmMain\kotlin\org\kosat\`

____________
</details>

## Heuristics and Features

<details>
  <summary>Main features implemented in solver</summary>

1. ReNumeration
2. Trail
3. Conflict analysis
4. Backjump
5. Propagation
6. 2-watched literals
7. VSIDS
8. Luby Restarts
9. Phase saving
10. ReduceDB based on LBD

</details>

## Documentation

Our solver has a detailed documentation about everything you might
need to know. **It may be useful even if you are new to SAT problem.**


> Check this out [here](docs/docs.md).

## Contribution & Support
KoSAT is an open source project. If you have found any bugs
or want to suggest some effective heuristics for solver, we are
open for your help! 

If you have any troubles while using our solver, you can contact
us in telegram:
@AlxVlsv, @dvrr9, @polinarria
