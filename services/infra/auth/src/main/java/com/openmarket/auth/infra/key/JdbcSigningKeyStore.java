package com.openmarket.auth.infra.key;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.openmarket.common.core.id.Ids;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JWT 서명 키를 auth 스키마({@code signing_key})에 저장한다. 처음 기동할 때 RSA 2048 키를 하나 만든다.
 *
 * <p>여러 인스턴스가 동시에 떠도 키가 하나만 생기도록 advisory lock 안에서 확인하고 만든다. 서명은 가장 최근의 ACTIVE 키로 한다.
 */
public class JdbcSigningKeyStore {

    private static final String ALGORITHM = "RS256";
    private static final int RSA_BITS = 2048;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final SigningKeyCipher cipher;
    private final Clock clock;

    public JdbcSigningKeyStore(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            SigningKeyCipher cipher,
            Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** 서명에 쓸 키(개인 키 포함). 없으면 만든다. */
    public RSAKey loadOrCreate() {
        return Objects.requireNonNull(
                transaction.execute(
                        status -> {
                            jdbc.sql("SELECT pg_advisory_xact_lock(hashtext('auth.signing_key'))")
                                    .query()
                                    .singleRow();
                            List<RSAKey> active = load("status = 'ACTIVE'");
                            return active.isEmpty() ? create() : active.getFirst();
                        }));
    }

    /** JWK 세트에 실을 키. 회전 뒤에도 이전 토큰을 검증할 수 있게 RETIRED가 아닌 키를 모두 싣는다. */
    public List<RSAKey> published() {
        return load("status <> 'RETIRED'");
    }

    private List<RSAKey> load(String condition) {
        return jdbc.sql(
                        "SELECT id, public_key, private_key FROM signing_key WHERE "
                                + condition
                                + " ORDER BY created_at DESC")
                .query(
                        (rs, row) ->
                                toKey(
                                        rs.getString("id"),
                                        rs.getString("public_key"),
                                        rs.getString("private_key")))
                .list();
    }

    private RSAKey create() {
        KeyPair pair;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_BITS);
            pair = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("서명 키를 만들지 못했다", e);
        }
        String id = Ids.newId().toString();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String privateKey = cipher.encrypt(pair.getPrivate().getEncoded());
        jdbc.sql(
                        "INSERT INTO signing_key (id, algorithm, public_key, private_key, status,"
                                + " created_at) VALUES (:id, :algorithm, :public, :private, 'ACTIVE',"
                                + " :now)")
                .param("id", id)
                .param("algorithm", ALGORITHM)
                .param("public", publicKey)
                .param("private", privateKey)
                .param("now", Timestamp.from(clock.instant()))
                .update();
        return toKey(id, publicKey, privateKey);
    }

    private RSAKey toKey(String id, String publicKey, String encryptedPrivateKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey pub =
                    (RSAPublicKey)
                            factory.generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
            RSAPrivateKey priv =
                    (RSAPrivateKey)
                            factory.generatePrivate(
                                    new PKCS8EncodedKeySpec(cipher.decrypt(encryptedPrivateKey)));
            return new RSAKey.Builder(pub)
                    .privateKey(priv)
                    .keyID(id)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("저장된 서명 키를 읽지 못했다: " + id, e);
        }
    }
}
