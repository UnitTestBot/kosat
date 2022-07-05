import csstype.Display
import csstype.px
import csstype.rgb
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
    var response by useState(props.time)
    var time by useState(props.response)

    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
        }
        +"Kotlin-based SAT solver, v0.1"
    }
    textarea {
        css {
            display = Display.block
            padding = 5.px
            backgroundColor = rgb(128, 128, 128)
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
        + "CHECK SAT"
    }

    label {
        css {
            display = Display.block
            padding = 5.px
            color = rgb(0, 0, 137)
        }
        + "$time sec"
    }

    pre {
        css {
            display = Display.block
            padding = 5.px
        }
        + response
    }
}