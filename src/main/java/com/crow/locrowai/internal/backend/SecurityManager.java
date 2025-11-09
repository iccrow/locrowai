package com.crow.locrowai.internal.backend;

import com.crow.locrowai.api.registration.exceptions.MissingSecurityKeyException;
import com.crow.locrowai.internal.LocrowAI;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static com.crow.locrowai.internal.LocrowAI.MODID;

@ApiStatus.Internal
public class SecurityManager {

    public record KeyMap(ClassLoader loader, Path path) {}

    private static final Map<KeyMap, PublicKey> keyCache = new HashMap<>();

    public static final PublicKey OFFICIAL_KEY = getKey(new KeyMap(LocrowAI.class.getClassLoader(), Path.of(MODID + "/public_key.pem")));

    private static final int MAX_SALT_LEN;

    static {
        RSAPublicKey rsa = (RSAPublicKey) OFFICIAL_KEY;
        int modulusBits = rsa.getModulus().bitLength();
        int emLen = (modulusBits + 7) / 8;
        final int hLen = 32; // SHA-256 digest length in bytes
        MAX_SALT_LEN = emLen - hLen - 2;
    }

    public static PublicKey getKey(KeyMap map) {
        if (keyCache.containsKey(map))
            return keyCache.get(map);

        InputStream stream = map.loader.getResourceAsStream(map.path.toString());
        if (stream == null) throw new MissingSecurityKeyException(map.path.toString());

        String pem = null;
        try {
            pem = new String(stream.readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        byte[] keyBytes = Base64.getDecoder().decode(pem);

        PublicKey key;
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            key = keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
        keyCache.put(map, key);
        return key;
    }

    static boolean verifyHash(Path path, String hash) throws IOException {
        try {
            byte[] fBytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hBytes = digest.digest(fBytes);
            return hash.equalsIgnoreCase(HexFormat.of().formatHex(hBytes));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    public static boolean verifySignature(byte[] fileBytes, byte[] sigBytes, PublicKey key) {
        try {
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(new PSSParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    MAX_SALT_LEN,
                    1
            ));
            signature.initVerify(key);
            signature.update(fileBytes);
            return signature.verify(sigBytes);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            return false;
        }
    }
}
