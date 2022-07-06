import csstype.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.css.css
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.textarea
import react.useState
import org.kosat.processCnfRequests
import org.kosat.readCnfRequests
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.dom.svg.AlignmentBaseline
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

external interface WelcomeProps : Props {
    var request: String
    var time: String
    var response: String
}

@OptIn(ExperimentalTime::class)
val Welcome = FC<WelcomeProps> { props ->
    var request by useState(props.request)
    var response by useState(props.response)
    var time by useState(props.time)

    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
            textAlign = TextAlign.center
            marginBottom = 10.px
            fontFamily = FontFamily.monospace
        }
        +"Kotlin-based SAT solver, v0.1"
    }

    div {
        css {
            marginBottom = 20.px
            fontFamily = FontFamily.monospace
            fontSize = 15.px
        }

        label {
            css {
                display = Display.block
                padding = 5.px
                color = rgb(0, 0, 137)
            }
            +"Put your CNF in DIMACS format here"
        }

        textarea {
            css {
                display = Display.block
                padding = 5.px
                backgroundColor = rgb(100, 100, 100)
                color = rgb(56, 246, 137)
            }
            rows = 25
            cols = 80
            value = request
            onChange = { event ->
                request = event.target.value
            }
        }


        button {
            css {
                display = Display.block
                padding = 5.px
                marginTop = 10.px
            }
            onClick = { event ->
                GlobalScope.launch {
                    val timed = measureTimedValue {
                        try {
                            val cnf = readCnfRequests(request)
                            processCnfRequests(cnf)
                        } catch (e: Throwable) {
                            e.toString()
                        }
                    }
                    time = timed.duration.toString(DurationUnit.SECONDS, 6)
                    response = timed.value
                }
            }
            +"CHECK SAT"
        }

        label {
            css {
                display = Display.block
                padding = 5.px
                color = rgb(0, 0, 137)
            }
            +"$time sec"
        }

        pre {
            css {
                display = Display.block
                padding = 5.px
            }
            +response
        }
    }

    div {
        css {
            fontFamily = FontFamily.monospace
        }
        h2 {
            css {
                display = Display.block
                padding = 5.px
                color = rgb(0, 0, 137)
            }
            +"DIMACS CNF format:"
        }
        p {
            css {
                display = Display.block
                padding = 5.px
                color = rgb(0, 0, 137)
            }
            +"The number of variables and the number of clauses is defined by the line \"p cnf variables clauses\""
        }
    }
}