import kotlinx.browser.document
import react.create
import react.dom.render

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val welcome = Welcome.create {
        request = "PUT your CNF in DIMACS format here"
        response = ""
        time = "0.00"
    }
    render(welcome, container)
}