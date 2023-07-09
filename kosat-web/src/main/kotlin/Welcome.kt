import csstype.Display
import csstype.FontFamily
import csstype.FontWeight
import csstype.Margin
import csstype.Position
import csstype.TextAlign
import csstype.px
import csstype.rgb
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.kosat.processCnfRequests
import org.kosat.readCnfRequests
import react.FC
import react.Props
import react.css.css
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.textarea
import react.useState
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

external interface WelcomeProps : Props {
    var request: String
    var time: String
    var response: String
}

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
            padding = 5.px
            display = Display.block
            fontFamily = FontFamily.monospace
        }

        label {
            css {
                display = Display.block
                marginBottom = 20.px
                color = rgb(0, 0, 137)
            }
            +"Put your CNF in DIMACS format here"
        }

        textarea {
            css {
                display = Display.block
                marginBottom = 20.px
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
                marginBottom = 20.px
                display = Display.block
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
                marginBottom = 20.px
                display = Display.block
                color = rgb(0, 0, 137)
            }
            +"$time sec"
        }

        pre {
            css {
                display = Display.block
            }
            +response
        }
    }

    div {
        css {
            position = Position.absolute
            display = Display.block
            marginRight = 0.px
            marginLeft = Margin("auto")

            fontFamily = FontFamily.monospace
            padding = 5.px
            color = rgb(0, 0, 137)
        }
        h2 { +"DIMACS CNF format:" }
        p { +"The number of variables and the number of clauses is defined by the line \"p cnf variables clauses\"." }
        p {
            +"Each of the next lines specifies a clause: a positive literal is denoted by the corresponding number, and a negative literal is denoted by the corresponding negative number. "
            br {}
            +"The last number in a line should be zero. For example:"
        }
        p {
            css {
                fontWeight = FontWeight.bold
            }
            +"p cnf 3 2"
            br {}
            +"1 2 -3 0"
            br {}
            +"-2 3 0"
        }
    }
}
