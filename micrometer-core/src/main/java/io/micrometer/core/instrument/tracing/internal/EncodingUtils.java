/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.tracing.internal;

import java.util.Arrays;

/**
 * Adopted from OpenTelemetry API.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public final class EncodingUtils {

	static final int LONG_BYTES = Long.SIZE / Byte.SIZE;

	static final int BYTE_BASE16 = 2;

	static final int LONG_BASE16 = BYTE_BASE16 * LONG_BYTES;

	private static final ThreadLocal<char[]> charBuffer = new ThreadLocal<>();

	private static final String ALPHABET = "0123456789abcdef";

	private static final int ASCII_CHARACTERS = 128;

	private static final char[] ENCODING = buildEncodingArray();

	private static final byte[] DECODING = buildDecodingArray();

	private EncodingUtils() {
	}

	private static char[] buildEncodingArray() {
		char[] encoding = new char[512];
		for (int i = 0; i < 256; ++i) {
			encoding[i] = ALPHABET.charAt(i >>> 4);
			encoding[i | 0x100] = ALPHABET.charAt(i & 0xF);
		}
		return encoding;
	}

	private static byte[] buildDecodingArray() {
		byte[] decoding = new byte[ASCII_CHARACTERS];
		Arrays.fill(decoding, (byte) -1);
		for (int i = 0; i < ALPHABET.length(); i++) {
			char c = ALPHABET.charAt(i);
			decoding[c] = (byte) i;
		}
		return decoding;
	}

	/**
	 * Returns the {@code long} value.
	 *
	 * @param chars the base8 or base16 representation of the {@code long}
	 * @return long array from string. Either contains high and low or just low trace id
	 */
	public static long[] fromString(CharSequence chars) {
		if (chars == null || chars.length() == 0) {
			return new long[] {0};
		}
		if (chars.length() == 32) {
			long high = HexCodec.lenientLowerHexToUnsignedLong(chars, 0, 16);
			long low = HexCodec.lenientLowerHexToUnsignedLong(chars, 16, 32);
			return new long[] {high, low};
		}
		return new long[] {HexCodec.lenientLowerHexToUnsignedLong(chars, 0, 16)};
	}

	/**
	 * Returns the {@code long} value whose base16 representation is stored in the first
	 * 16 chars of {@code chars} starting from the {@code offset}.
	 *
	 * @param chars the base16 representation of the {@code long}
	 * @return long value from string
	 */
	public static long longFromBase16String(CharSequence chars) {
		return longFromBase16String(chars, 0);
	}

	/**
	 * Returns the {@code long} value whose base16 representation is stored in the first
	 * 16 chars of {@code chars} starting from the {@code offset}.
	 *
	 * @param chars the base16 representation of the {@code long}
	 * @return long from base16 string
	 */
	static long longFromBase16String(CharSequence chars, int offset) {
        isTrue(chars.length() >= offset + LONG_BASE16, "chars too small");
		return (decodeByte(chars.charAt(offset), chars.charAt(offset + 1)) & 0xFFL) << 56
				| (decodeByte(chars.charAt(offset + 2), chars.charAt(offset + 3)) & 0xFFL) << 48
				| (decodeByte(chars.charAt(offset + 4), chars.charAt(offset + 5)) & 0xFFL) << 40
				| (decodeByte(chars.charAt(offset + 6), chars.charAt(offset + 7)) & 0xFFL) << 32
				| (decodeByte(chars.charAt(offset + 8), chars.charAt(offset + 9)) & 0xFFL) << 24
				| (decodeByte(chars.charAt(offset + 10), chars.charAt(offset + 11)) & 0xFFL) << 16
				| (decodeByte(chars.charAt(offset + 12), chars.charAt(offset + 13)) & 0xFFL) << 8
				| (decodeByte(chars.charAt(offset + 14), chars.charAt(offset + 15)) & 0xFFL);
	}

    private static void isTrue(boolean expression, String text) {
        if (!expression) {
            throw new IllegalStateException(text);
        }
    }

	/**
	 * Decodes the specified two character sequence, and returns the resulting
	 * {@code byte}.
	 *
	 * @param chars the character sequence to be decoded
	 * @param offset the starting offset in the {@code CharSequence}.
	 * @return the resulting {@code byte}
	 * @throws IllegalArgumentException if the input is not a valid encoded string
	 * according to this encoding
	 */
	public static byte byteFromBase16String(CharSequence chars, int offset) {
        isTrue(chars.length() >= offset + 2, "chars too small");
		return decodeByte(chars.charAt(offset), chars.charAt(offset + 1));
	}

	private static byte decodeByte(char hi, char lo) {
        isTrue(lo < ASCII_CHARACTERS && DECODING[lo] != -1, "invalid character " + lo);
        isTrue(hi < ASCII_CHARACTERS && DECODING[hi] != -1, "invalid character " + hi);
		int decoded = DECODING[hi] << 4 | DECODING[lo];
		return (byte) decoded;
	}

	/**
	 * Checks if string is valid base16.
	 *
	 * @param value to check
	 * @return {@code true} if valid base16 string
	 */
	public static boolean isValidBase16String(CharSequence value) {
		for (int i = 0; i < value.length(); i++) {
			char b = value.charAt(i);
			// 48..57 && 97..102 are valid
			if (!isDigit(b) && !isLowercaseHexCharacter(b)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Converts long into string.
	 *
	 * @param id 64 bit
	 * @return string representation of the long
	 */
	public static String fromLong(long id) {
		return fromLongs(0, id);
	}

	/**
	 * Converts longs into string.
	 *
	 * @param idHigh - trace id high part
	 * @param idLow - trace id low part
	 * @return string representation of the long
	 */
	public static String fromLongs(long idHigh, long idLow) {
		if (idHigh == 0L) {
			return HexCodec.toLowerHex(idLow);
		}
		else {
			char[] chars = getTemporaryBuffer();
			longToBase16String(idHigh, chars, 0);
			longToBase16String(idLow, chars, 16);
			return new String(chars);
		}
	}

	/**
	 * Converts the long to base16 string.
	 *
	 * @param value value to convert
	 * @param dest destination array
	 * @param destOffset offset
	 */
	public static void longToBase16String(long value, char[] dest, int destOffset) {
		byteToBase16((byte) ((int) (value >> 56 & 255L)), dest, destOffset);
		byteToBase16((byte) ((int) (value >> 48 & 255L)), dest, destOffset + 2);
		byteToBase16((byte) ((int) (value >> 40 & 255L)), dest, destOffset + 4);
		byteToBase16((byte) ((int) (value >> 32 & 255L)), dest, destOffset + 6);
		byteToBase16((byte) ((int) (value >> 24 & 255L)), dest, destOffset + 8);
		byteToBase16((byte) ((int) (value >> 16 & 255L)), dest, destOffset + 10);
		byteToBase16((byte) ((int) (value >> 8 & 255L)), dest, destOffset + 12);
		byteToBase16((byte) ((int) (value & 255L)), dest, destOffset + 14);
	}

	/**
	 * Converts the byte to base16 string.
	 *
	 * @param value value to convert
	 * @param dest destination array
	 * @param destOffset offset
	 */
	public static void byteToBase16(byte value, char[] dest, int destOffset) {
		int b = value & 255;
		dest[destOffset] = ENCODING[b];
		dest[destOffset + 1] = ENCODING[b | 256];
	}

	private static char[] getTemporaryBuffer() {
		char[] chars = charBuffer.get();
		if (chars == null) {
			chars = new char[32];
			charBuffer.set(chars);
		}
		return chars;
	}

	private static boolean isLowercaseHexCharacter(char b) {
		return 97 <= b && b <= 102;
	}

	private static boolean isDigit(char b) {
		return 48 <= b && b <= 57;
	}

	// taken from brave.internal.codec.HexCodec
	static final class HexCodec {

		static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

		private HexCodec() {
			throw new IllegalStateException("Can't instantiate a utility class");
		}

		/**
		 * Parses a 16 character lower-hex string with no prefix into an unsigned long,
		 * starting at the specified index.
		 *
		 * This reads a trace context a sequence potentially larger than the format. The
		 * use-case is reducing garbage, by re-using the input {@code value} across multiple
		 * parse operations.
		 * @param value the sequence that contains a lower-hex encoded unsigned long.
		 * @param beginIndex the inclusive begin index: {@linkplain CharSequence#charAt(int)
		 * index} of the first lower-hex character representing the unsigned long.
		 */
		static long lowerHexToUnsignedLong(CharSequence value, int beginIndex) {
			int endIndex = Math.min(beginIndex + 16, value.length());
			long result = lenientLowerHexToUnsignedLong(value, beginIndex, endIndex);
			if (result == 0) {
				throw isntLowerHexLong(value);
			}
			return result;
		}

		/**
		 * Like {@link #lowerHexToUnsignedLong(CharSequence, int)}, but returns zero on
		 * invalid input.
		 * @param value the sequence that contains a lower-hex encoded unsigned long.
		 * @param beginIndex the inclusive begin index: {@linkplain CharSequence#charAt(int)
		 * index} of the first lower-hex character representing the unsigned long.
		 * @param endIndex the exclusive end index: {@linkplain CharSequence#charAt(int)
		 * index} after the last lower-hex character representing the unsigned long.
		 */
		static long lenientLowerHexToUnsignedLong(CharSequence value, int beginIndex, int endIndex) {
			long result = 0;
			int pos = beginIndex;
			while (pos < endIndex) {
				char c = value.charAt(pos++);
				result <<= 4;
				if (c >= '0' && c <= '9') {
					result |= c - '0';
				}
				else if (c >= 'a' && c <= 'f') {
					result |= c - 'a' + 10;
				}
				else {
					return 0;
				}
			}
			return result;
		}

		static NumberFormatException isntLowerHexLong(CharSequence lowerHex) {
			throw new NumberFormatException(lowerHex + " should be a 1 to 32 character lower-hex string with no prefix");
		}

		/** Inspired by {@code okio.Buffer.writeLong}. */
		static String toLowerHex(long v) {
			char[] data = RecyclableBuffers.parseBuffer();
			writeHexLong(data, 0, v);
			return new String(data, 0, 16);
		}

		/** Inspired by {@code okio.Buffer.writeLong}. */
		static void writeHexLong(char[] data, int pos, long v) {
			writeHexByte(data, pos + 0, (byte) ((v >>> 56L) & 0xff));
			writeHexByte(data, pos + 2, (byte) ((v >>> 48L) & 0xff));
			writeHexByte(data, pos + 4, (byte) ((v >>> 40L) & 0xff));
			writeHexByte(data, pos + 6, (byte) ((v >>> 32L) & 0xff));
			writeHexByte(data, pos + 8, (byte) ((v >>> 24L) & 0xff));
			writeHexByte(data, pos + 10, (byte) ((v >>> 16L) & 0xff));
			writeHexByte(data, pos + 12, (byte) ((v >>> 8L) & 0xff));
			writeHexByte(data, pos + 14, (byte) (v & 0xff));
		}

		static void writeHexByte(char[] data, int pos, byte b) {
			data[pos + 0] = HEX_DIGITS[(b >> 4) & 0xf];
			data[pos + 1] = HEX_DIGITS[b & 0xf];
		}

	}

	// taken from brave
	static final class RecyclableBuffers {

		private static final ThreadLocal<char[]> PARSE_BUFFER = new ThreadLocal<>();

		private RecyclableBuffers() {
			throw new IllegalStateException("Can't instantiate a utility class");
		}

		/**
		 * Returns a {@link ThreadLocal} reused {@code char[]} for use when decoding bytes
		 * into an ID hex string. The buffer should be immediately copied into a
		 * {@link String} after decoding within the same method.
		 */
		static char[] parseBuffer() {
			char[] idBuffer = PARSE_BUFFER.get();
			if (idBuffer == null) {
				idBuffer = new char[32 + 1 + 16 + 3 + 16]; // traceid128-spanid-1-parentid
				PARSE_BUFFER.set(idBuffer);
			}
			return idBuffer;
		}

	}

}
