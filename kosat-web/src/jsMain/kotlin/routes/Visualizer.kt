package routes

import WrapperCommand
import cdclDispatchContext
import cdclWrapperContext
import components.HelpTooltip
import emotion.react.css
import js.core.jso
import mui.material.Box
import mui.material.Paper
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Fragment
import react.Props
import react.PropsWithChildren
import react.ReactNode
import react.create
import react.dom.html.ReactHTML.pre
import react.useContext
import react.useEffect
import sections.ActionsSection
import sections.AssignmentSection
import sections.ClauseDbSection
import sections.ConflictSection
import sections.HistorySection
import sections.InputSection
import sections.StateSection
import sections.TrailSection
import web.cssom.AlignSelf
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.GridArea
import web.cssom.GridTemplateAreas
import web.cssom.Length
import web.cssom.TextAlign
import web.cssom.array
import web.cssom.fr
import web.cssom.ident
import web.cssom.pct
import web.cssom.pt
import web.dom.document

external interface SectionPaperProps : PropsWithChildren {
    var gridArea: GridArea
    var title: String
    var help: ReactNode?
    var maxHeight: Length?
}

/**
 * A [Paper] for each section of the visualizer.
 */
private val SectionPaper: FC<SectionPaperProps> = FC("SectionPaper") { props ->
    Paper {
        elevation = 3

        css {
            padding = 8.pt
            display = Display.flex
            flexDirection = FlexDirection.column
            gridArea = props.gridArea
            gap = 8.pt
            maxHeight = props.maxHeight
        }

        Typography {
            sx {
                alignSelf = AlignSelf.center
                textAlign = TextAlign.center
            }
            variant = TypographyVariant.h2
            +props.title

            if (props.help != null) {
                HelpTooltip {
                    +props.help
                }
            }
        }

        +props.children
    }
}

/**
 * The main visualizer component.
 *
 * It is composed of several sections, each of which displays a different
 * aspect of the solver.
 */
val Visualizer: FC<Props> = FC("Visualizer") { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    fun dispatchIfPossible(command: WrapperCommand) {
        if (solver.canExecute(command)) {
            dispatch(command)
        }
    }

    useEffect(solver) {
        document.onkeydown = { event ->
            when {
                event.ctrlKey && event.key == "z" -> {
                    dispatchIfPossible(WrapperCommand.Undo())
                    event.preventDefault()
                }

                event.key == "ArrowUp" -> {
                    dispatchIfPossible(WrapperCommand.Undo(weak = true))
                    event.preventDefault()
                }

                event.ctrlKey && event.key == "y" -> {
                    dispatchIfPossible(WrapperCommand.Redo())
                    event.preventDefault()
                }

                event.ctrlKey && event.key == "Z" -> {
                    dispatchIfPossible(WrapperCommand.Redo())
                    event.preventDefault()
                }

                event.key == "ArrowDown" -> {
                    dispatchIfPossible(WrapperCommand.Redo(weak = true))
                    event.preventDefault()
                }

                event.key == " " -> {
                    val next = solver.nextAction
                    if (next != null) {
                        dispatch(next)
                    }
                    event.preventDefault()
                }
            }
        }

        cleanup {
            document.onkeydown = null
        }
    }

    Typography {
        variant = TypographyVariant.h1

        sx {
            textAlign = TextAlign.center
            marginBottom = (-8).pt
        }

        +"KoSAT Visualization"
    }
    Box {
        sx {
            display = Display.grid
            gap = 8.pt
            padding = 8.pt
            gridTemplateColumns = array(30.pct, 1.fr, 20.pct)
            gridTemplateRows = array(125.pt, 240.pt, 130.pt, 180.pt)
            gridTemplateAreas = GridTemplateAreas(
                arrayOf(ident("input"), ident("state"), ident("trail")),
                arrayOf(ident("input"), ident("db"), ident("trail")),
                arrayOf(ident("assignment"), ident("assignment"), ident("trail")),
                arrayOf(ident("history"), ident("analysis"), ident("actions")),
            )
        }

        SectionPaper {
            gridArea = ident("input")
            title = "Input"

            InputSection {}

            help = Fragment.create {
                Typography {
                    +"The input is parsed as a DIMACS CNF."
                }

                Typography {
                    +"""
                        The file starts with a header, which is a line starting with "p cnf",
                        followed by the number of variables and the number of clauses.
                    """.trimIndent()
                }

                Typography {
                    +"""
                        The rest of the file consists of clauses, one per line. Each clause
                        is a list of literals, separated by spaces. A literal is a positive
                        or negative integer, where the absolute value is the variable number,
                        and the sign indicates the polarity of the literal.
                    """.trimIndent()
                }

                Typography {
                    +"""
                        For example, the following is a valid input file:
                    """.trimIndent()
                }

                Box {
                    component = pre
                    +"""
                        p cnf 3 2
                        1 -2 3 0
                        2 0
                    """.trimIndent()
                }
            }
        }

        SectionPaper {
            gridArea = ident("history")
            title = "Time Travel"
            HistorySection {}

            help = Fragment.create {
                Typography {
                    +"""
                        The history shows the state of the solver at each step. You can
                        click on a step to go back or forth in time and see the state 
                        of the solver at that point.
                    """.trimIndent()
                }

                Typography {
                    dangerouslySetInnerHTML = jso {
                        // language=html
                        __html = """
                            You can also use the arrow keys (up and down) to go back and 
                            forth in time, or the undo and redo buttons, with their respective
                            keyboard shortcuts: <kbd>Ctrl</kbd>+<kbd>Z</kbd> and 
                            <kbd>Ctrl</kbd>+<kbd>Y</kbd> 
                            (<kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>Z</kbd>).
                        """.trimIndent()
                    }
                }
            }
        }

        solver.state.inner.run {
            SectionPaper {
                gridArea = ident("state")
                title = "Solver State"
                StateSection {}

                help = Fragment.create {
                    Typography {
                        +"""
                            General information about the current solver state.
                        """.trimIndent()
                    }
                }
            }

            SectionPaper {
                gridArea = ident("db")
                title = "Clause Database"
                ClauseDbSection {}

                help = Fragment.create {
                    Typography {
                        +"""
                            The clause database contains all the clauses that are currently
                            known to the solver. Clauses are separated into two categories:
                            irredundant (or original) and redundant (or learnts). 
                        """.trimIndent()
                    }

                    Typography {
                        +"""
                            Irredundant clauses are the clauses that were present in the
                            input, and in the implementation used here, they don't change.
                        """.trimIndent()
                    }

                    Typography {
                        +"""
                            Redundant clauses are the clauses that were learned by the solver
                            during the search. They are generated by the conflict analysis
                            procedure, and are used to guide the search. They can be removed
                            from the database at almost any time, and the CNF problem will 
                            still be equivalent to the original one.
                        """.trimIndent()
                    }
                }
            }

            SectionPaper {
                gridArea = ident("assignment")
                title = "Assignment"
                AssignmentSection {}

                help = Fragment.create {
                    Typography {
                        +"""
                            The assignment is a mapping from variables to truth values. It
                            represents the current state of the solver.
                            
                            Click on "+" and "-" icons to assign a variable to true or false,
                            respectively. Use backtracking (see Trail) to undo assignments.
                            
                            Assigned literals are shown are outlined in green or red,
                            if they are true or false, respectively, and fully filled if 
                            the assignment is fixed (that is, assigned at level 0).
                        """.trimIndent()
                    }
                }
            }
        }

        SectionPaper {
            gridArea = ident("trail")
            title = "Trail"
            TrailSection {}

            help = Fragment.create {
                Typography {
                    +"""
                        The trail is a list of literals that were assigned during the
                        search, in the order they were assigned. It has a lot of uses,
                        but the most important ones are propagating and backtracking.
                    """.trimIndent()
                }

                Typography {
                    +"""
                        Propagating is the process of assigning literals that are implied
                        by the current assignment.
                    """.trimIndent()
                }

                Typography {
                    +"""
                        Backtracking is the process of undoing assignments by un-assigning
                        literals from the trail, until the given decision level is reached.
                    """.trimIndent()
                }
            }
        }

        SectionPaper {
            gridArea = ident("analysis")
            title = "Conflict Analysis"
            ConflictSection {}

            help = Fragment.create {
                Typography {
                    +"""
                        When propagation causes a clause to be falsified, a conflict is
                        observed. The conflict analysis procedure is to "trace back" the
                        reason for the conflict, and learn a new clause that prevents the
                        same conflict from happening again.
                    """.trimIndent()
                }

                Typography {
                    +"""
                        Analysis is done by repeatedly replacing a literal in the conflict
                        clause with its reason, until only one literal from the last
                        decision level remains. This literal is called the UIP (Unique 
                        Implication Point) literal. By backtracking to the second highest level
                        of the resulting clause, we are able to assign the UIP literal to
                        true.
                    """.trimIndent()
                }

                Typography {
                    +"""
                        If the conflict occurs at level 0, the problem is unsatisfiable.
                    """.trimIndent()
                }
            }
        }

        SectionPaper {
            gridArea = ident("actions")
            title = "Actions"
            ActionsSection {}

            help = Fragment.create {
                Typography {
                    dangerouslySetInnerHTML = jso {
                        // language=html
                        __html = """
                            General solver functionality.
                            
                            Press the "Next" button (or press <kbd>Space</kbd>) to
                            execute the next action in the solver.
                        """.trimIndent()
                    }
                }
            }
        }
    }
}