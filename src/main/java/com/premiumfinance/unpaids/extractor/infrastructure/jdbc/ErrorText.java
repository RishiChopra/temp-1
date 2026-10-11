package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

/** Error and reason texts are stored in VARCHAR2(4000) columns. */
final class ErrorText {

    private static final int MAX_LENGTH = 3900;

    private ErrorText() {
    }

    static String fitted(String text) {
        return text == null || text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}
