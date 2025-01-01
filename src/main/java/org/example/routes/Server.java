package org.example.routes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.example.Main;
import org.example.Constants;

public class Server extends AbstractVerticle
{
    public void start(Promise<Void> promise)
    {
        var router = Router.router(Main.vertx);

        //A)Credential Module : Credential Router for handling Credential routes
        var credentialRouter = Router.router(Main.vertx);

        router.route("/api/v1/credentials/*").handler(BodyHandler.create()).subRouter(credentialRouter);

        new Credentials().route(credentialRouter);

        //B)Discovery Module : Discovery Router for handling Discovery routes
        var discoveryRouter = Router.router(Main.vertx);

        router.route("/api/v1/discovery/*").handler(BodyHandler.create()).subRouter(discoveryRouter);

        new Discovery().route(discoveryRouter);

        //C)Provision Module : Provision Router for handling Provision routes
        var provisionRouter = Router.router(Main.vertx);

        router.route("/api/v1/provision/*").subRouter(provisionRouter);

        new Provision().route(provisionRouter);

        router.get("/api/v1/").handler(ctx -> ctx.response()
                .setStatusCode(200)
                .end(new JsonObject()
                        .put("status.code",200)
                        .put("message","Welcome to Homepage")
                        .put("data","Root endpoint of API").encodePrettily()));

        router.get("/notfound").handler(ctx-> ctx.response().setStatusCode(404).
                end(new JsonObject()
                        .put("status.code",404)
                        .put("message","Not found")
                        .put("data","Requested endpoint doesn't exist").encodePrettily()));

        router.route().failureHandler(ctx->
        {
            if(ctx.statusCode()==404)
            {
                ctx.reroute("/notfound");
            }
            else
            {
                ctx.response().setStatusCode(500).
                        end(new JsonObject()
                                .put("status.code",404)
                                .put("message","Error occurred")
                                .put("error",ctx.failure()).encodePrettily());
            }
        });

        router.route().handler(ctx ->
        {
            ctx.fail(404); // Manually trigger a 404 for unmatched routes
        });

        vertx.createHttpServer()

                .exceptionHandler(handler->promise.fail(handler.getCause().getMessage()))

                .requestHandler(router)

                .listen(Constants.HTTP_PORT, http->{

                    if(http.succeeded())
                    {
                        promise.complete();
                    }
                    else
                    {
                        promise.fail("Not able to listen on the port: "+ Constants.HTTP_PORT);
                    }
                });
    }
}
