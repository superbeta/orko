package com.grahamcrockford.oco.websocket;

import static com.grahamcrockford.oco.marketdata.MarketDataType.OPEN_ORDERS;
import static com.grahamcrockford.oco.marketdata.MarketDataType.ORDERBOOK;
import static com.grahamcrockford.oco.marketdata.MarketDataType.TICKER;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.security.RolesAllowed;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.grahamcrockford.oco.auth.Roles;
import com.grahamcrockford.oco.marketdata.ExchangeEventRegistry;
import com.grahamcrockford.oco.marketdata.MarketDataSubscription;
import com.grahamcrockford.oco.marketdata.MarketDataType;
import com.grahamcrockford.oco.marketdata.OpenOrdersEvent;
import com.grahamcrockford.oco.marketdata.OrderBookEvent;
import com.grahamcrockford.oco.marketdata.TickerEvent;
import com.grahamcrockford.oco.notification.NotificationEvent;
import com.grahamcrockford.oco.spi.TickerSpec;
import com.grahamcrockford.oco.websocket.OcoWebSocketOutgoingMessage.Nature;

@Metered
@Timed
@ExceptionMetered
@ServerEndpoint("/ws")
@RolesAllowed(Roles.TRADER)
public final class OcoWebSocketServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(OcoWebSocketServer.class);

  private final String eventRegistryClientId = OcoWebSocketServer.class.getSimpleName() + "/" + UUID.randomUUID().toString();

  @Inject private ExchangeEventRegistry exchangeEventRegistry;
  @Inject private ObjectMapper objectMapper;
  @Inject private AsyncEventBus eventBus;

  private Session session;

  private final AtomicReference<ImmutableSet<MarketDataSubscription>> marketDataSubscriptions = new AtomicReference<>(ImmutableSet.of());

  @OnOpen
  public void myOnOpen(final javax.websocket.Session session) throws IOException, InterruptedException {
    LOGGER.info("Opening socket");
    injectMembers(session);
    this.session = session;
    eventBus.register(this);
  }

  @OnMessage
  public void myOnMsg(final javax.websocket.Session session, String message) {
    OcoWebSocketIncomingMessage request = null;
    try {

      request = decodeRequest(message);

      switch (request.command()) {
        case CHANGE_TICKERS:
          mutateSubscriptions(TICKER, request.tickers());
          break;
        case CHANGE_OPEN_ORDERS:
          mutateSubscriptions(OPEN_ORDERS, request.tickers());
          break;
        case CHANGE_ORDER_BOOK:
          mutateSubscriptions(ORDERBOOK, request.tickers());
          break;
        case UPDATE_SUBSCRIPTIONS:
          updateSubscriptions(session);
          break;
        default:
          // Jackson should stop this happening in the try block above, but just for completeness
          throw new IllegalArgumentException("Invalid command: " + request.command());
      }

    } catch (Exception e) {
      LOGGER.error("Error processing message: " + message, e);
      session.getAsyncRemote().sendText(message(Nature.ERROR, "Error processing message"));
      return;
    }
  }


  private void mutateSubscriptions(MarketDataType marketDataType, Iterable<TickerSpec> tickers) {
    marketDataSubscriptions.set(ImmutableSet.<MarketDataSubscription>builder()
      .addAll(FluentIterable.from(marketDataSubscriptions.get()).filter(sub -> !sub.type().equals(marketDataType)))
      .addAll(FluentIterable.from(tickers).transform(spec -> MarketDataSubscription.create(spec, marketDataType)))
      .build()
    );
  }

  @OnClose
  public void myOnClose(final javax.websocket.Session session, CloseReason cr) {
    LOGGER.info("Closing socket ({})", cr.toString());
    try {
      eventBus.unregister(this);
    } catch (Throwable t) {
      LOGGER.error("Error unregistering socket from notification", t);
    }
    try {
      marketDataSubscriptions.set(ImmutableSet.of());
      exchangeEventRegistry.clearSubscriptions(marketDataSubscriptions.get(), eventRegistryClientId);
    } catch (Throwable t) {
      LOGGER.error("Error unregistering socket from ticker", t);
    }
  }

  @OnError
  public void onError(Throwable error) {
    LOGGER.error("Socket error", error);
  }

  private void injectMembers(final javax.websocket.Session session) {
    Injector injector = (Injector) session.getUserProperties().get(Injector.class.getName());
    injector.injectMembers(this);
  }

  private OcoWebSocketIncomingMessage decodeRequest(String message) {
    OcoWebSocketIncomingMessage request;
    try {
      request = objectMapper.readValue(message, OcoWebSocketIncomingMessage.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid request", e);
    }
    return request;
  }

  private void updateSubscriptions(Session session) {
    LOGGER.info("Updating subscriptions for socket to: {}", marketDataSubscriptions);
    exchangeEventRegistry.changeSubscriptions(marketDataSubscriptions.get(), eventRegistryClientId, this::onTicker, this::onOpenOrders, this::onOrderBook);
  }

  @Subscribe
  void onNotification(NotificationEvent notificationEvent) {
    send(notificationEvent, Nature.NOTIFICATION);
  }

  void onTicker(TickerEvent tickerEvent) {
    send(tickerEvent, Nature.TICKER);
  }

  void onOpenOrders(OpenOrdersEvent openOrdersEvent) {
    send(openOrdersEvent, Nature.OPEN_ORDERS);
  }

  void onOrderBook(OrderBookEvent orderBookEvent) {
    send(orderBookEvent, Nature.ORDERBOOK);
  }

  void send(Object object, Nature nature) {
    LOGGER.debug("{}: {}", nature, object);
//    try {
      session.getAsyncRemote().sendText(message(nature, object));
//    } catch (IOException e) {
//      LOGGER.info("Failed to send " + object + " to socket", e);
//    }
  }

  private String message(Nature nature, Object data) {
    try {
      return objectMapper.writeValueAsString(OcoWebSocketOutgoingMessage.create(nature, data));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}