package dropship.logging;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import dropship.Settings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class StatsdStatsLogger {

  static final class NoopLogger extends StatsdStatsLogger {

    NoopLogger(Settings settings) {
      super(settings);
    }

    @Override
    protected void doSend(String stat) {
      // no-op
    }
  }

  static final class StatsdStatsLoggerImpl extends StatsdStatsLogger {

    private final Logger logger;
    private final InetSocketAddress address;
    private final DatagramChannel channel;

    StatsdStatsLoggerImpl(Settings settings,
                          Logger logger,
                          String host,
                          int port) {
      super(settings);

      this.logger = checkNotNull(logger, "logger");

      try {
        this.address = new InetSocketAddress(host, port);
        this.channel = DatagramChannel.open();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    StatsdStatsLoggerImpl(Settings settings, Logger logger, String host) {
      this(settings, logger, host, 8125);
    }

    @Override
    protected void doSend(String stat) {
      try {
        byte[] rawBytesToSend = stat.getBytes(Charsets.UTF_8.name());
        ByteBuffer bytesToSend = ByteBuffer.wrap(rawBytesToSend);
        int bytesSent = channel.send(bytesToSend, address);

        if (rawBytesToSend.length != bytesSent) {
          logger.warn(
            "Could not send entirety of stat {} to host {}:{}. Only sent {}/{} bytes.",
            stat,
            address.getHostName(),
            address.getPort(),
            bytesSent,
            rawBytesToSend.length
          );
        }

      } catch (IOException e) {
        logger.warn(e, "Exception thrown while sending stats");
      }
    }
  }

  public static final CharMatcher DISALLOWED_CHARS = CharMatcher.is('@');
  private final Random rng;
  private final double defaultSampleRate;

  private StatsdStatsLogger(Settings settings) {

    this.rng = new Random();
    this.defaultSampleRate = settings.statsdSampleRate();
  }

  public final void timing(String key, long value) {
    timing(key, value, defaultSampleRate);
  }

  public final void timing(String key, long value, double sampleRate) {
    send(key + ':' + value + "|ms", sampleRate);
  }

  public final void increment(String key, long magnitude) {
    increment(key, magnitude, defaultSampleRate);
  }

  public final void increment(String key, long magnitude, double sampleRate) {
    String stat = key + ':' + magnitude + "|c";
    send(stat, sampleRate);
  }

  public final void gauge(String key, long value) {
    gauge(key, value, defaultSampleRate);
  }

  public final void gauge(String key, long value, double sampleRate) {
    String stat = key + ':' + value + "|g";
    send(stat, sampleRate);
  }

  private void send(String stat, double sampleRate) {
    if (sampleRate <= 0.0) {
      return;
    }

    if (sampleRate >= 1.0 || rng.nextDouble() <= sampleRate) {
      stat = escape(stat) + "|@" + sampleRate;
      doSend(stat);
    }
  }

  private String escape(String stat) {
    return DISALLOWED_CHARS.replaceFrom(stat, '-');
  }

  protected abstract void doSend(String stat);
}