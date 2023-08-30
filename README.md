# Pure Kotlin CDCL SAT solver

[![CI](https://github.com/UnitTestBot/kosat/actions/workflows/ci.yml/badge.svg)](https://github.com/UnitTestBot/kosat/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/UnitTestBot/kosat.svg)](https://jitpack.io/p/UnitTestBot/kosat)
![MIT](https://img.shields.io/badge/license-MIT-blue)

> KoSAT is pure Kotlin CDCL SAT solver based on MiniSat
> with some techniques included from other
> state-of-the-art SAT solvers.

## 🔥 Web-based CDCL visualizer

> https://www.utbot.org/kosat/

## 📦 Kotlin Multiplatform

KoSAT is implemented as Kotlin Multiplatform project.
Core CDCL algorithm is written as a common module,
so you can use KoSAT on any supported platform,
e.g. JVM or JS.

### Kotlin/JVM

Add the JitPack repository and dependency
in your `build.gradle.kts`:

```kts
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.UnitTestBot.kosat:core:$version")
}
```

#### Usage example: Kotlin

```kotlin
import org.kosat.CDCL

fun main() {
    // Create the instance of KoSAT solver:
    val solver = CDCL()

    // Allocate two variables:
    solver.newVariable()
    solver.newVariable()

    // Encode the TIE-SHIRT problem:
    solver.newClause(-1, 2)
    solver.newClause(1, 2)
    solver.newClause(-1, -2)
    // solver.newClause(1, -2) // UNSAT with this clause

    // Solve the SAT problem:
    val result = solver.solve()
    println("result = $result") // SAT

    // Get the model:
    val model = solver.getModel()
    println("model = $model") // [false, true]
}
```

Find more about KoSAT interface [here](docs/interface.md).

<details>
  <summary>Main features implemented in solver</summary>
<br/>

1. [ReNumeration](docs/numeration.md)
2. [Trail](docs/trail.md)
3. [Conflict analysis](docs/analyze.md)
4. [Backjump](docs/backjump.md)
5. [Propagation](docs/propagation.md)
6. [2-watched literals](docs/watched%20literals.md)
7. [VSIDS](docs/branching.md)
8. [Luby restarts](docs/restarts.md)
9. [Polarity choice](docs/polarity%20choice.md)
10. [ReduceDB based on LBD](docs/reduceDB.md)
11. [Incremental solving](docs/incremental.md)

</details>

## 📚 Documentation

Our solver has a detailed documentation
about everything you might need to know.
**It may be useful even if you are new to the SAT topic.**

> Check the docs [here](docs/docs.md).

## ⭐ Contribution & Support

KoSAT is an open source project.
If you have found any bugs or want to suggest
some effective heuristics for solver,
we are open for your help!

## 📜 License

[![MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
