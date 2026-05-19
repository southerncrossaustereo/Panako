/***************************************************************************
*                                                                          *
* Panako - acoustic fingerprinting                                         *
* Copyright (C) 2014 - 2022 - Joren Six / IPEM                             *
*                                                                          *
* This program is free software: you can redistribute it and/or modify     *
* it under the terms of the GNU Affero General Public License as           *
* published by the Free Software Foundation, either version 3 of the       *
* License, or (at your option) any later version.                          *
*                                                                          *
* This program is distributed in the hope that it will be useful,          *
* but WITHOUT ANY WARRANTY; without even the implied warranty of           *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
* GNU Affero General Public License for more details.                      *
*                                                                          *
* You should have received a copy of the GNU Affero General Public License *
* along with this program.  If not, see <http://www.gnu.org/licenses/>     *
*                                                                          *
****************************************************************************
*    ______   ________   ___   __    ________   ___   ___   ______         *
*   /_____/\ /_______/\ /__/\ /__/\ /_______/\ /___/\/__/\ /_____/\        *
*   \:::_ \ \\::: _  \ \\::\_\\  \ \\::: _  \ \\::.\ \\ \ \\:::_ \ \       *
*    \:(_) \ \\::(_)  \ \\:. `-\  \ \\::(_)  \ \\:: \/_) \ \\:\ \ \ \      *
*     \: ___\/ \:: __  \ \\:. _    \ \\:: __  \ \\:. __  ( ( \:\ \ \ \     *
*      \ \ \    \:.\ \  \ \\. \`-\  \ \\:.\ \  \ \\: \ )  \ \ \:\_\ \ \    *
*       \_\/     \__\/\__\/ \__\/ \__\/ \__\/\__\/ \__\/\__\/  \_____\/    *
*                                                                          *
****************************************************************************
*                                                                          *
*                              Panako                                      *
*                       Acoustic Fingerprinting                            *
*                                                                          *
****************************************************************************/



package be.panako.strategy.panako;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialize and deserialize {@link PanakoFingerprint} lists for transport between
 * a fingerprint-extracting strategy instance and a separate lookup-only instance.
 * The wire format carries only the data needed by the query path
 * ({@code hash}, {@code t1}, {@code f1}) and uses a fixed-width binary header so
 * receivers can reject blobs they do not understand.
 */
public final class PanakoFingerprintCodec {

	/** Magic bytes "EWRP" identifying an EarWorm fingerprint blob. */
	public static final int MAGIC = 0x45575250;

	/** Wire format revision. Increment on any incompatible change. */
	public static final byte VERSION = 0x01;

	/** Strategy identifier reserved for the Panako strategy. */
	public static final byte STRATEGY_PANAKO = 0x01;

	private static final int HEADER_BYTES = 12;
	private static final int PRINT_BYTES = 16;

	private PanakoFingerprintCodec(){}

	/**
	 * Serialize a list of fingerprints to a self-describing byte array.
	 * @param prints The fingerprints to encode.
	 * @return A byte array containing the header and packed fingerprint entries.
	 */
	public static byte[] encode(List<PanakoFingerprint> prints){
		ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + prints.size() * PRINT_BYTES);
		buf.putInt(MAGIC);
		buf.put(VERSION);
		buf.put(STRATEGY_PANAKO);
		buf.putShort((short) 0);
		buf.putInt(prints.size());
		for(PanakoFingerprint print : prints){
			buf.putLong(print.hash());
			buf.putInt(print.t1);
			buf.putInt(print.f1);
		}
		return buf.array();
	}

	/**
	 * Deserialize a byte array previously produced by {@link #encode}. Validates
	 * the magic and version header bytes and throws {@link RuntimeException} on
	 * mismatch so callers fail loud rather than silently returning empty results.
	 * @param blob The serialized fingerprint blob.
	 * @return The decoded fingerprints.
	 */
	public static List<PanakoFingerprint> decode(byte[] blob){
		if(blob == null || blob.length < HEADER_BYTES){
			throw new RuntimeException("Fingerprint blob is too short to contain a header");
		}
		ByteBuffer buf = ByteBuffer.wrap(blob);
		int magic = buf.getInt();
		if(magic != MAGIC){
			throw new RuntimeException(String.format(
					"Fingerprint blob magic mismatch: expected 0x%08X got 0x%08X", MAGIC, magic));
		}
		byte version = buf.get();
		if(version != VERSION){
			throw new RuntimeException(String.format(
					"Fingerprint blob version mismatch: expected %d got %d", VERSION, version));
		}
		byte strategy = buf.get();
		if(strategy != STRATEGY_PANAKO){
			throw new RuntimeException(String.format(
					"Fingerprint blob strategy mismatch: expected %d (Panako) got %d", STRATEGY_PANAKO, strategy));
		}
		buf.getShort(); // reserved
		int count = buf.getInt();
		int expected = HEADER_BYTES + count * PRINT_BYTES;
		if(blob.length != expected){
			throw new RuntimeException(String.format(
					"Fingerprint blob length mismatch: header declares %d entries (%d bytes) but blob is %d bytes",
					count, expected, blob.length));
		}
		List<PanakoFingerprint> prints = new ArrayList<>(count);
		for(int i = 0; i < count; i++){
			long hash = buf.getLong();
			int t1 = buf.getInt();
			int f1 = buf.getInt();
			prints.add(new PanakoFingerprint(hash, t1, f1));
		}
		return prints;
	}
}
