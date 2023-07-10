import kotlinx.browser.document
import react.create
import react.dom.render

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val welcome = Welcome.create {
        request = "p cnf 3 2\n1 2 -3 0\n-2 3 0"
        response = ""
        time = "0.00"
    }
    render(welcome, container)
}