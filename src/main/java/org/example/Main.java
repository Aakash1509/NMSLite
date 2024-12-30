package org.example;
import io.vertx.core.Vertx;
import org.example.database.LoadDB;
import org.example.poll.Poller;
import org.example.poll.Scheduler;
import org.example.routes.Server;
import org.example.sevices.FileSender;
import org.example.sevices.FileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    public static final Vertx vertx = Vertx.vertx();

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args)
    {
            vertx.deployVerticle(new LoadDB())

                .compose(result->vertx.deployVerticle(new Server()))

                .compose(result -> vertx.deployVerticle(new Scheduler()))

                .compose(result -> vertx.deployVerticle(new Poller()))

                .compose(result -> vertx.deployVerticle(new FileWriter()))

                .compose(result-> vertx.deployVerticle(new FileSender()))

                .onComplete(result ->
                {
                    if (result.succeeded())
                    {
                        logger.info("All verticles deployed successfully");
                    }
                    else
                    {
                        logger.error("Error deploying verticles", result.cause());
                    }
                });
    }

}