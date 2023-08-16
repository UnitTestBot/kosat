import react.create
import react.dom.client.createRoot
import web.dom.document
import web.html.HTML.div

fun main() {
    val container = document.createElement(div)
    document.body.appendChild(container)
    val app = App.create()
    createRoot(container).render(app)
}
