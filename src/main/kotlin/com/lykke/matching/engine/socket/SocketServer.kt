package com.lykke.matching.engine.socket

import com.lykke.matching.engine.AppInitialData
import com.lykke.matching.engine.messages.MessageProcessor
import com.lykke.matching.engine.messages.MessageWrapper
import com.lykke.matching.engine.utils.config.Config
import com.lykke.utils.logging.MetricsLogger
import com.lykke.utils.logging.ThrottlingLogger
import java.net.ServerSocket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.regex.Pattern

class SocketServer(private val config: Config, private val initializationCompleteCallback: (AppInitialData) -> Unit): Runnable {

    companion object {
        val LOGGER = ThrottlingLogger.getLogger(SocketServer::class.java.name)
        val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    private val messagesQueue: BlockingQueue<MessageWrapper> = LinkedBlockingQueue<MessageWrapper>()
    private val connections = CopyOnWriteArraySet<ClientHandler>()

    override fun run() {
        val maxConnections = config.me.socket.maxConnections
        val clientHandlerThreadPool = Executors.newFixedThreadPool(maxConnections)

        val messageProcessor = MessageProcessor(config, messagesQueue)
        messageProcessor.start()

        initializationCompleteCallback(messageProcessor.appInitialData)

        val port = config.me.socket.port
        val socket = ServerSocket(port)
        LOGGER.info("Waiting connection on port: $port.")
        try {
            while (true) {
                val clientConnection = socket.accept()
                if (isConnectionAllowed(getWhiteList(), clientConnection.inetAddress.hostAddress)) {
                    val handler = ClientHandler(messagesQueue, clientConnection, this)
                    clientHandlerThreadPool.submit(handler)
                    connect(handler)
                } else {
                    clientConnection.close()
                    LOGGER.info("Connection from host ${clientConnection.inetAddress.hostAddress} is not allowed.")
                }
            }
        } catch (exception: Exception) {
            LOGGER.error("Got exception: ", exception)
            METRICS_LOGGER.logError( "Fatal exception", exception)
        } finally {
            socket.close()
        }
    }

    private fun isConnectionAllowed(whitelist: List<String>?, host: String): Boolean {
        if (whitelist != null) {
            whitelist.forEach {
                if (Pattern.compile(it).matcher(host).matches()) {
                    return true
                }
            }
            return false
        }
        return true
    }

    private fun getWhiteList() : List<String>? {
        if (config.me.whiteList != null) {
            return config.me.whiteList.split(";")
        }
        return null
    }

    private fun connect(handler: ClientHandler) {
        connections.add(handler)
    }

    fun disconnect(handler: ClientHandler) {
        connections.remove(handler)
    }
}