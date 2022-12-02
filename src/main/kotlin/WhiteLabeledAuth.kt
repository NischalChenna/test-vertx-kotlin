import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch


class WhiteLabeledAuth : CoroutineVerticle() {
    private var shuttingDown = false
    fun <T> Route.coroutineHandler(fn: suspend (RoutingContext) -> T) = handler {
        launch(it.vertx().dispatcher()) {
            try {
                val any = fn(it)
                val data = if (any !is Unit) any else null

                it.response()
                    .setStatusCode(200).putHeader("content-type", "application/json")
                    .putHeader("Access-Control-Allow-Origin", "http://localhost:3000")
                    .putHeader("Access-Control-Allow-Methods","GET, POST, OPTIONS")
                    .putHeader("Access-Control-Allow-Credentials", "false")
                    .end(Json.encode(JsonObject().also {
                        it.put("success", true)
                        it.put("data", data)
                    }))
            } catch (e: Exception) {
                it.fail(e)
            }
        }
    }
    fun <A, B> Route.coroutineHandlerEarlyResponse(fn: suspend (RoutingContext) -> Pair<A, B>, postFn: suspend (B) -> Unit) = handler {
        launch(it.vertx().dispatcher()) {
            var any: Pair<A, B>? = null

            try {
                any = fn(it)
                val data = if (any.first !is Unit) any.first else null

                it.response()
                    .setStatusCode(200)
                    .end(Json.encode(JsonObject().also {
                        it.put("success", true)
                        it.put("data", data)
                    }))


            } catch (e: Exception) {
                it.fail(e)
            }

            try {
                any?.second?.let { second -> postFn(second) }
            } catch (e : Exception) {
                println(e.message)
                println(e.printStackTrace())
            }
        }
    }
    fun createAuthSessionToken(originOrgId: String, originOrgName: String, originUserEmail: String): String{
        return (originOrgId.hashCode()*originOrgName.hashCode()*originUserEmail.hashCode()).toString()
    }
    private fun addOne(routingContext: RoutingContext) {
        println(routingContext.request());
//        routingContext.response()
//            .setStatusCode(201)
//            .putHeader("content-type", "application/json; charset=utf-8")
//            .end(Json.encodePrettily(whisky))
    }
    override suspend fun start() {
        val router: Router = Router.router(vertx)
        router.route().handler{//5xx series is universally used for retrial, i.e., caller will retry in some time.
            //do not send 400 here as 400 is treated as service error and no retry will be done.
            if (shuttingDown){
                it.response().setStatusCode(500).end()
            }
            else it.next()}
        router.get("/health").coroutineHandler { "ok" } //health check
        router.post("/auth.session.create").coroutineHandler {
            val body = it.request().body().await().toJsonObject()
            val originOrgId = body.getString("originOrgId")
            val orginOrgName = body.getString("orginOrgName")
            val originUserEmail = body.getString("originUserEmail")
            createAuthSessionToken(originOrgId, orginOrgName, originUserEmail)
        }
        router.get("/apps.get").coroutineHandler { JsonArray("[{\"category\":[\"HRIS\"],\"appId\":\"bamboohr\",\"label\":\"BambooHR\",\"logo\":\"https://someurl\",\"oAuthUrl\":\"https://oauth.url\",\"authType\":\"OAUTH\",\"setupDetails\":[{\"label\":\"OrganizationURL\",\"id\":\"\",\"uiElementType\":\"TEXTBOX\",\"uiElementDataType\":\"STRING\",\"description\":\"\",\"isRequired\":\"true\",\"optionData\":\"\",\"default\":\"\",\"tip\":\"\"},{\"label\":\"ClientID\",\"id\":\"\",\"uiElementType\":\"TEXTBOX\",\"uiElementDataType\":\"STRING\",\"description\":\"\",\"isRequired\":\"true\",\"optionData\":\"\",\"default\":\"\",\"tip\":\"\"},{\"label\":\"ClientSecret\",\"id\":\"\",\"uiElementType\":\"TEXTBOX\",\"uiElementDataType\":\"STRING\",\"description\":\"\",\"isRequired\":\"true\",\"optionData\":\"\",\"default\":\"\",\"tip\":\"\"},{\"label\":\"APIKey\",\"id\":\"\",\"uiElementType\":\"TEXTBOX\",\"uiElementDataType\":\"STRING\",\"description\":\"\",\"isRequired\":\"true\",\"optionData\":\"\",\"default\":\"\",\"tip\":\"\"}]}]") }
//        router.get("/greetPerson").coroutineHandler { json {
//            obj(
//                "message" to "token" to createAuthSessionToken
//            ).encode() } }//health check
//        router.get("/messages").handler(buildMessageByQueryString)
//        router.post("/messages").handler(BodyHandler.create()).handler(buildMessageByPOST)
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(1323)
            .onSuccess { println("HTTP server started on port " + it.actualPort()) }
            .onFailure { println("Failed to start HTTP server:" + it.message) }

    }
}