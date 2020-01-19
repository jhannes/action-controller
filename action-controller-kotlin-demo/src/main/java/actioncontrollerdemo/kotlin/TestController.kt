package actioncontrollerdemo.kotlin

import org.actioncontroller.ContentBody
import org.actioncontroller.Get
import org.actioncontroller.RequestParam
import org.actioncontroller.json.JsonBody
import org.jsonbuddy.JsonObject
import java.util.*
import kotlin.jvm.internal.Reflection
import kotlin.jvm.internal.ReflectionFactory

class TestController {
    private var updater: Runnable

    constructor() {
        updater = Runnable {}
    }

    constructor(updater: Runnable) {
        this.updater = updater
    }

    @Get("/test")
    @ContentBody
    fun sayHello(
            @RequestParam("name") name: String? = "world"
    ): String {
        return "Hello ${name ?: "there"}"
    }

    @Get("/update")
    fun update() {
        updater.run()
    }


    @Get("/json")
    @JsonBody
    fun json(): JsonObject {
        return JsonObject().put("product", "Blåbærsyltetøy")
    }
}
