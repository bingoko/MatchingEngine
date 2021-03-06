package com.lykke.matching.engine.services

import com.lykke.matching.engine.AbstractTest
import com.lykke.matching.engine.daos.Asset
import com.lykke.matching.engine.daos.AssetPair
import com.lykke.matching.engine.database.buildWallet
import com.lykke.matching.engine.order.OrderStatus
import com.lykke.matching.engine.outgoing.messages.LimitOrdersReport
import com.lykke.matching.engine.outgoing.messages.MarketOrderWithTrades
import com.lykke.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.lykke.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.lykke.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class MarketOrderService_Dust_Test: AbstractTest() {
    
    companion object {
        private const val DELTA = 1e-9
    }

    @Before
    fun setUp() {
        testBackOfficeDatabaseAccessor.addAsset(Asset("LKK", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("SLR", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("EUR", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("GBP", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("CHF", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("USD", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("JPY", 2))
        testBackOfficeDatabaseAccessor.addAsset(Asset("BTC", 8))
        testBackOfficeDatabaseAccessor.addAsset(Asset("BTC1", 8))

        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("EURJPY", "EUR", "JPY", 3))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("BTCUSD", "BTC", "USD", 8))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("BTCLKK", "BTC", "LKK", 6))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("BTC1USD", "BTC1", "USD", 3))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("BTC1LKK", "BTC1", "LKK", 6))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("SLRBTC", "SLR", "BTC", 8))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("SLRBTC1", "SLR", "BTC1", 8))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("LKKEUR", "LKK", "EUR", 5))
        testDictionariesDatabaseAccessor.addAssetPair(AssetPair("LKKGBP", "LKK", "GBP", 5))
        initServices()
    }

    @After
    fun tearDown() {
    }

    @Test
    fun testDustMatchOneToOne() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", price = 1000.0, volume = 1000.0, clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 1500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC", 0.020009))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTCUSD", volume = -0.02)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(1000.0, marketOrderReport.order.price!!, DELTA)
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.02000000", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("20.00", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("USD", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)

        Assert.assertEquals(0.02, testWalletDatabaseAccessor.getBalance("Client3", "BTC"), DELTA)
        Assert.assertEquals(1480.0, testWalletDatabaseAccessor.getBalance("Client3", "USD"), DELTA)
        Assert.assertEquals(0.000009, testWalletDatabaseAccessor.getBalance("Client4", "BTC"), DELTA)
        Assert.assertEquals(20.0, testWalletDatabaseAccessor.getBalance("Client4", "USD"), DELTA)
    }

    @Test
    fun testDustIncorrectBalanceAndDust1() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(assetId = "BTC1USD", price = 610.96, volume = 1000.0, clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 1500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.14441494999999982))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = 88.23, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(610.96, marketOrderReport.order.price!!, DELTA)
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.14441208", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC1", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("88.23", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("USD", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)

        Assert.assertEquals(0.14441208, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"), DELTA)
        Assert.assertEquals(1500 - 88.23, testWalletDatabaseAccessor.getBalance("Client3", "USD"), DELTA)
        Assert.assertEquals(0.00000287, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"), DELTA)
        Assert.assertEquals(88.23, testWalletDatabaseAccessor.getBalance("Client4", "USD"), DELTA)
    }

    @Test
    fun testDustIncorrectBalanceAndDust2() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(assetId = "BTC1USD", price = 598.916, volume = 1000.0, clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 1500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.033407))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = 20.0, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(598.916, marketOrderReport.order.price!!, DELTA)
        Assert.assertEquals("20.0", marketOrderReport.order.volume.toString())
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.03339367", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC1", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("20.00", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("USD", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)

        Assert.assertEquals(0.03339367, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"), DELTA)
        Assert.assertEquals(1500 - 20.0, testWalletDatabaseAccessor.getBalance("Client3", "USD"), DELTA)
        Assert.assertEquals(0.00001333, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"), DELTA)
        Assert.assertEquals(20.0, testWalletDatabaseAccessor.getBalance("Client4", "USD"), DELTA)
    }

    @Test
    fun testDustIncorrectBalanceAndDust3() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(assetId = "BTC1USD", price = 593.644, volume = 1000.0, clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 1500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.00092519))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = 0.54, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(593.644, marketOrderReport.order.price!!, DELTA)
        Assert.assertEquals("0.54", marketOrderReport.order.volume.toString())
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.00090964", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC1", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("0.54", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("USD", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)

        Assert.assertEquals(0.00090964, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"), DELTA)
        Assert.assertEquals(1500 - 0.54, testWalletDatabaseAccessor.getBalance("Client3", "USD"), DELTA)
        Assert.assertEquals(0.00001555, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"), DELTA)
        Assert.assertEquals(0.54, testWalletDatabaseAccessor.getBalance("Client4", "USD"), DELTA)
    }

    @Test
    fun testDustNotStraight() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 500.0, assetId = "BTCUSD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC", 0.02001))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTCUSD", volume = 20.0, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(1000.0, marketOrderReport.order.price!!, DELTA)
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.02000000", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("20.00", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("USD", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)

        Assert.assertEquals(0.02, testWalletDatabaseAccessor.getBalance("Client3", "BTC"), DELTA)
        Assert.assertEquals(480.0, testWalletDatabaseAccessor.getBalance("Client3", "USD"), DELTA)
        Assert.assertEquals(20.0, testWalletDatabaseAccessor.getBalance("Client4", "USD"), DELTA)
        Assert.assertEquals(0.00001, testWalletDatabaseAccessor.getBalance("Client4", "BTC"), DELTA)
    }

    @Test
    fun testBuyDustStraight() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = -500.0, assetId = "BTC1USD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "BTC1", 0.02001))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "USD", 500.0))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = 0.0000272, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
    }

    @Test
    fun test_20170309_01() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 0.0000782, volume = -4000.0, assetId = "SLRBTC1", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "SLR", 238619.65864945))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.01))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "SLRBTC1", volume = 127.8722, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(127.8722, marketOrderReport.order.volume, DELTA)
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.00999961", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC1", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("127.87", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("SLR", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)
    }

    @Test
    fun test_20170309_02() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 0.0000782, volume = -4000.0, assetId = "SLRBTC1", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "SLR", 238619.65864945))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.01))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "SLRBTC1", volume = -0.01, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        Assert.assertEquals(-0.01, marketOrderReport.order.volume, DELTA)
        Assert.assertEquals(1, marketOrderReport.trades.size)

        Assert.assertEquals("0.01000000", marketOrderReport.trades.first().marketVolume)
        Assert.assertEquals("BTC1", marketOrderReport.trades.first().marketAsset)
        Assert.assertEquals("Client4", marketOrderReport.trades.first().marketClientId)
        Assert.assertEquals("127.87", marketOrderReport.trades.first().limitVolume)
        Assert.assertEquals("SLR", marketOrderReport.trades.first().limitAsset)
        Assert.assertEquals("Client3", marketOrderReport.trades.first().limitClientId)
    }

    @Test
    fun testSellDustStraight() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 500.0, assetId = "BTC1USD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.02001))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = -0.0000272, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
    }

    @Test
    fun testBuyDustNotStraight() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 19739.43939992, volume = 500.0, assetId = "BTC1LKK", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "LKK", 500.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 0.02001))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1LKK", volume = 0.01, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
    }

    @Test
    fun testSellDustNotStraight() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 19739.43939992, volume = -500.0, assetId = "BTC1LKK", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "BTC1", 0.02001))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "LKK", 500.0))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1LKK", volume = -0.01, straight = false)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
    }

    @Test
    fun testDust1() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = -0.05, assetId = "BTC1USD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "USD", 5000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "BTC1", 10.0))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = 0.04997355, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        assertEquals(1, clientsLimitOrdersQueue.size)
        val result = clientsLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(1, result.orders.size)
        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)

        assertEquals(0.04997355, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"))
        assertEquals(4950.02, testWalletDatabaseAccessor.getBalance("Client4", "USD"))
        assertEquals(10 - 0.04997355, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"))
        assertEquals(49.98, testWalletDatabaseAccessor.getBalance("Client3", "USD"))
    }

    @Test
    fun testDust2() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 0.05, assetId = "BTC1USD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 5000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 10.0))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = -0.04997355, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        assertEquals(1, clientsLimitOrdersQueue.size)
        val result = clientsLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(1, result.orders.size)
        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)

        assertEquals(0.04997355, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"))
        assertEquals(4950.03, testWalletDatabaseAccessor.getBalance("Client3", "USD"))
        assertEquals(10 - 0.04997355, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"))
        assertEquals(49.97, testWalletDatabaseAccessor.getBalance("Client4", "USD"))
    }

    @Test
    fun testDust3() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = -0.05, assetId = "BTC1USD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "USD", 5000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "BTC1", 10.0))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = 0.0499727, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        assertEquals(1, clientsLimitOrdersQueue.size)
        val result = clientsLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(1, result.orders.size)
        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)

        assertEquals(0.0499727, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"))
        assertEquals(4950.02, testWalletDatabaseAccessor.getBalance("Client4", "USD"))
        assertEquals(9.9500273, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"))
        assertEquals(49.98, testWalletDatabaseAccessor.getBalance("Client3", "USD"))
    }

    @Test
    fun testDust4() {
        testOrderDatabaseAccessor.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 0.05, assetId = "BTC1USD", clientId = "Client3"))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "USD", 5000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client4", "BTC1", 10.0))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(clientId = "Client4", assetId = "BTC1USD", volume = -0.0499727, straight = true)))

        Assert.assertEquals(1, rabbitSwapQueue.size)
        val marketOrderReport = rabbitSwapQueue.poll() as MarketOrderWithTrades
        Assert.assertEquals(OrderStatus.Matched.name, marketOrderReport.order.status)
        assertEquals(1, clientsLimitOrdersQueue.size)
        val result = clientsLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(1, result.orders.size)
        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)

        assertEquals(0.0499727, testWalletDatabaseAccessor.getBalance("Client3", "BTC1"))
        assertEquals(4950.03, testWalletDatabaseAccessor.getBalance("Client3", "USD"))
        assertEquals(9.9500273, testWalletDatabaseAccessor.getBalance("Client4", "BTC1"))
        assertEquals(49.97, testWalletDatabaseAccessor.getBalance("Client4", "USD"))
    }
}