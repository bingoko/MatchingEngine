package com.lykke.matching.engine.config.spring

import com.lykke.matching.engine.socket.SocketServer
import com.lykke.matching.engine.utils.config.Config
import com.lykke.utils.AppInitializer
import com.lykke.utils.AppVersion
import com.lykke.utils.alivestatus.processor.AliveStatusProcessorFactory
import com.lykke.utils.logging.MetricsLogger
import com.lykke.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class AppConfiguration {

    @Autowired
    private lateinit var config: Config

    @Bean
    fun socketServer(): Runnable {
        return SocketServer { appInitialData ->
            MetricsLogger.getLogger().logWarning("""Spot.${config.me.name} ${AppVersion.VERSION}
                |: Started : ${appInitialData.ordersCount} orders, ${appInitialData.balancesCount}
                |balances for ${appInitialData.clientsCount} clients""".trimMargin())
        }
    }

    @Bean
    fun azureStatusProcessor(): Runnable {
        return AliveStatusProcessorFactory
                .createAzureProcessor(connectionString = config.me.db.matchingEngineConnString,
                        appName = "MatchingEngine",
                        config = config.me.aliveStatus)
    }

    @PostConstruct
    fun init() {
        AppInitializer.init()
        MetricsLogger.init("ME", config.slackNotifications)
        ThrottlingLogger.init(config.throttlingLogger)
    }
}