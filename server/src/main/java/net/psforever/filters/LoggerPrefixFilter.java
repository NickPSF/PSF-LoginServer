// Copyright (c) 2021 PSForever
package net.psforever.filters;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Disrupts a variety of logging messages that originate from specific loggers.
 * A comparison of the prefix text of the logger handling the event is performed,
 * with a positive match denying that event being appended.
 * The full prefix must be provided, as the filter uses the fully authenticated name
 * and the logger occasionally displays an abbreviated form for longer names,
 * e.g., "i.g.context.jasync ..." instead of "io.getquill.context.jasync ...".
 */
public class LoggerPrefixFilter extends Filter<ILoggingEvent> {
    private String prefix;

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (isStarted() && event.getLoggerName().startsWith(prefix)) {
            return FilterReply.DENY;
        } else {
            return FilterReply.NEUTRAL;
        }
    }

    public void setPrefix(String name) {
        this.prefix = name;
    }

    @Override
    public void start() {
        if (this.prefix != null) {
            super.start();
        }
    }
}
