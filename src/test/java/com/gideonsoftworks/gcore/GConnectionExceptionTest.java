package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.ParseException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GConnectionExceptionTest {

    @Test
    void stringConstructorDefaultsToProtocolError() {
        GConnectionException ex = new GConnectionException("test error");
        assertEquals(GConnectionException.GCE_PROTOCOL_ERROR, ex.getType());
    }

    @Test
    void stringAndTypeConstructorStoresBoth() {
        GConnectionException ex = new GConnectionException("network issue", GConnectionException.GCE_NETWORK_ERROR);
        assertEquals(GConnectionException.GCE_NETWORK_ERROR, ex.getType());
        assertEquals("network issue", ex.getMessage());
    }

    @Test
    void parseExceptionConstructorSetsListingParserError() {
        ParseException pe = new ParseException("bad listing format", 5);
        GConnectionException ex = new GConnectionException(pe);
        assertEquals(GConnectionException.GCE_LISTING_PARSER_ERROR, ex.getType());
        assertEquals("bad listing format", ex.getMessage());
    }

    @Test
    void getMessageRoundTrip() {
        String msg = "something went wrong";
        GConnectionException ex = new GConnectionException(msg);
        assertEquals(msg, ex.getMessage());
    }

    @ParameterizedTest(name = "type {0} returns non-null description")
    @MethodSource("allTypesProvider")
    void getDescriptionReturnsNonNullForAllTypes(int type, String expectedDescription) {
        GConnectionException ex = new GConnectionException("test", type);
        String description = ex.getDescription();
        assertNotNull(description, "getDescription() should not be null for type " + type);
        assertEquals(expectedDescription, description);
    }

    static Stream<Arguments> allTypesProvider() {
        return Stream.of(
                Arguments.of(GConnectionException.GCE_CONNECTION_LOST, GLocalized.GCE_CONNECTION_LOST),
                Arguments.of(GConnectionException.GCE_OPERATION_NOT_SUPPORTED, GLocalized.GCE_OPERATION_NOT_SUPPORTED),
                Arguments.of(GConnectionException.GCE_PROTOCOL_ERROR, GLocalized.GCE_PROTOCOL_ERROR),
                Arguments.of(GConnectionException.GCE_SERVER_NEEDS_ATTENTION, GLocalized.GCE_SERVER_NEEDS_ATTENTION),
                Arguments.of(GConnectionException.GCE_LISTING_PARSER_ERROR, GLocalized.GCE_LISTING_PARSER_ERROR),
                Arguments.of(GConnectionException.GCE_NETWORK_ERROR, GLocalized.GCE_NETWORK_ERROR),
                Arguments.of(GConnectionException.GCE_BAD_LOGIN, GLocalized.GCE_BAD_LOGIN),
                Arguments.of(GConnectionException.GCE_BAD_FOLDER, GLocalized.GCE_BAD_FOLDER)
        );
    }

    @Test
    void errorTypeConstantsHaveExpectedValues() {
        assertEquals(0, GConnectionException.GCE_CONNECTION_LOST);
        assertEquals(1, GConnectionException.GCE_OPERATION_NOT_SUPPORTED);
        assertEquals(2, GConnectionException.GCE_PROTOCOL_ERROR);
        assertEquals(3, GConnectionException.GCE_SERVER_NEEDS_ATTENTION);
        assertEquals(4, GConnectionException.GCE_LISTING_PARSER_ERROR);
        assertEquals(5, GConnectionException.GCE_NETWORK_ERROR);
        assertEquals(6, GConnectionException.GCE_BAD_LOGIN);
        assertEquals(7, GConnectionException.GCE_BAD_FOLDER);
    }

    @Test
    void getTypeReturnsCorrectValueForEachConstructorVariant() {
        GConnectionException fromString = new GConnectionException("error msg");
        assertEquals(GConnectionException.GCE_PROTOCOL_ERROR, fromString.getType());

        GConnectionException fromStringAndType = new GConnectionException("error msg", GConnectionException.GCE_BAD_LOGIN);
        assertEquals(GConnectionException.GCE_BAD_LOGIN, fromStringAndType.getType());

        ParseException pe = new ParseException("parse fail", 0);
        GConnectionException fromParseException = new GConnectionException(pe);
        assertEquals(GConnectionException.GCE_LISTING_PARSER_ERROR, fromParseException.getType());
    }

    @Test
    void exceptionIsThrowable() {
        GConnectionException ex = new GConnectionException("throwable test");
        assertInstanceOf(Exception.class, ex);
    }
}
