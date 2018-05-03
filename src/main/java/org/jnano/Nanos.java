package org.jnano;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.jnano.Hexes.*;
import static org.jnano.Preconditions.checkArgument;

public final class Nanos {
    public static final String SEED_REGEX = "^[A-Z0-9]{64}$";
    public static final String ADDRESS_REGEX = "^(xrb_)[13456789abcdefghijkmnopqrstuwxyz]{60}$";

    private Nanos() {
    }

    /**
     * Generate seed using SecureRandom
     *
     * @return random 32 bytes seed
     * @throws ActionNotSupportedException Strong SecureRandom is not available
     * @see SecureRandom#getInstanceStrong()
     */
    @Nonnull
    public static String generateSeed() {
        try {
            SecureRandom secureRandom = SecureRandom.getInstanceStrong();
            byte[] seed = new byte[32];
            secureRandom.nextBytes(seed);
            return toHex(seed);
        } catch (NoSuchAlgorithmException e) {
            throw new ActionNotSupportedException("Seed generation not supported", e);
        }
    }

    /**
     * Deterministically create address from a seed in a given index
     *
     * @param seed
     * @param index
     * @return Nano address (xrb_1111111111111111111111111111111111111111111111111111hifc8npp)
     */
    @Nonnull
    public static String createAddress(@Nonnull String seed, int index) {
        checkArgument(seed.matches(SEED_REGEX), () -> "Invalid seed " + seed);
        checkArgument(index >= 0, () -> "Invalid index " + index);

        byte[] privateKey = Hashes.digest256(toByteArray(seed), ByteBuffer.allocate(4).putInt(index).array()); //digest 36 bytes into 32
        byte[] publicKey = PublicKeys.generate(privateKey);
        return toAddress(publicKey);
    }

    /**
     * Extract public key from a Address
     *
     * @param address
     * @return public key
     */
    @Nonnull
    public static byte[] toPublicKey(@Nonnull String address) {
        checkArgument(address.matches(ADDRESS_REGEX), () -> "Invalid address " + address);

        String encodedPublicKey = address.substring(4, 56);
        String encodedChecksum = address.substring(56);

        String binaryPublicKey = AddressEncodes.decode(encodedPublicKey).substring(4);

        String hexPublicKey = StringUtils.leftPad(Hexes.toHex(binaryPublicKey), 64);

        byte[] publicKey = toByteArray(hexPublicKey);

        checkEncodedChecksum(encodedChecksum, publicKey);

        return publicKey;
    }

    /**
     * Create address to a given public key
     *
     * @param publicKey
     * @return address
     */
    @Nonnull
    public static String toAddress(@Nonnull byte[] publicKey) {
        checkArgument(publicKey.length == 32, () -> "Invalid public key" + Arrays.toString(publicKey));

        String binaryPublicKey = StringUtils.leftPad(toBinary(toHex(publicKey)), 260); //we get the address by picking

        String encodedChecksum = calculateEncodedChecksum(publicKey);
        String encodedPublicKey = AddressEncodes.encode(binaryPublicKey);

        //return the address prefixed with xrb_ and suffixed with
        return "xrb_" + encodedPublicKey + encodedChecksum;
    }

    @Nonnull
    public static String hashOpenBlock(@Nonnull String source, @Nonnull String representative, @Nonnull String account) {
        return hash(toByteArray(source), toPublicKey(representative), toPublicKey(account));
    }

    @Nonnull
    public static String hashSendBlock(@Nonnull String previous, @Nonnull String destination, @Nonnull BigInteger balance) {
        String hexBalance = toHex(balance);
        return hash(toByteArray(previous), toPublicKey(destination), toByteArray(hexBalance));
    }

    @Nonnull
    public static String hashReceiveBlock(@Nonnull String previous, @Nonnull String source) {
        return hash(toByteArray(previous), toByteArray(source));
    }

    @Nonnull
    public static String hashChangeBlock(@Nonnull String previous, @Nonnull String representative) {
        return hash(toByteArray(previous), toPublicKey(representative));
    }

//    @Nonnull
//    public static String hashStateBlock(@Nonnull String account, @Nonnull String previous, @Nonnull String representative, @Nonnull BigInteger balance, @Nonnull String link) {
//        String hexBalance = toHex(balance);
//        return hash(
//                toPublicKey(account),
//                toByteArray(previous),
//                toPublicKey(representative),
//                toByteArray(hexBalance),
//                link.startsWith("xrb_") ? toPublicKey(link) : toByteArray(link)
//        );
//    }

    private static String hash(byte[]... byteArrays) {
        return Hexes.toHex(Hashes.digest256(byteArrays));
    }

    private static void checkEncodedChecksum(String expectedEncodedChecksum, byte[] publicKey) {
        String encodedChecksum = calculateEncodedChecksum(publicKey);
        checkArgument(expectedEncodedChecksum.equals(encodedChecksum), () -> "Invalid checksum " + expectedEncodedChecksum);
    }

    private static String calculateEncodedChecksum(byte[] publicKey) {
        byte[] checksum = reverse(Hashes.digest(5, publicKey));
        String binaryChecksum = StringUtils.leftPad(toBinary(toHex(checksum)), checksum.length * 8);
        return AddressEncodes.encode(binaryChecksum);
    }

    private static byte[] reverse(byte[] b) {
        byte[] bb = new byte[b.length];
        for (int i = b.length; i > 0; i--) {
            bb[b.length - i] = b[i - 1];
        }
        return bb;
    }

    public static class ActionNotSupportedException extends RuntimeException {
        private ActionNotSupportedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
