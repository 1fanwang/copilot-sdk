/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *--------------------------------------------------------------------------------------------*/

package com.github.copilot.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The origin of a message sent to a Copilot session.
 * <p>
 * Set on {@link MessageOptions#setSource(MessageSource)} to distinguish user
 * input from programmatic context. This does not configure the session's system
 * prompt or change the message delivery mode.
 *
 * @see MessageOptions
 */
public enum MessageSource {

    /** Input originating from the user. */
    USER("user"),

    /** Programmatic context or an automated message. */
    SYSTEM("system");

    private final String value;

    MessageSource(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON value for this message source.
     *
     * @return the string value used in JSON serialization
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Deserializes a JSON string into the corresponding message source.
     *
     * @param value
     *            the JSON string value
     * @return the matching source, or {@code null} if value is {@code null}
     * @throws IllegalArgumentException
     *             if the value does not match a known message source
     */
    @JsonCreator
    public static MessageSource fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MessageSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown MessageSource value: " + value);
    }
}
