package io.github.devmrko.companion.adapter;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.RegionProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Loads an OCI CLI profile as SDK request signing details.
 *
 * <p>API key profiles use the SDK's {@link ConfigFileAuthenticationDetailsProvider}. Session profiles made by
 * {@code oci session authenticate} have {@code security_token_file} and no {@code user}; they sign with key id
 * {@code ST$<token>}. The token file is read on every request, so {@code oci session refresh} or a new login takes
 * effect without restarting the app. The user OCID of a session profile is the token's {@code sub} claim. No
 * background refresh thread is started; an expired session fails the request (401) like any other auth error.
 */
final class OciProfiles {
    private static final Pattern USER=Pattern.compile("ocid1\\.user\\.[a-z0-9]+\\.[a-z0-9-]*\\.[a-z0-9]+");
    private static final long MAX_TOKEN=65536;

    private OciProfiles() {}

    static AuthenticationDetailsProvider load(String configPath,String profile) throws IOException {
        var config=ConfigFileReader.parse(configPath,profile);
        var tokenFile=config.get("security_token_file");
        if (tokenFile==null||tokenFile.isBlank()) return new ConfigFileAuthenticationDetailsProvider(config);
        return new SessionProfile(config,configPath);
    }

    /** CLI {@code --auth} value for commands such as {@code oci ce cluster generate-token}. */
    static String cliAuth(String configPath,String profile) {
        try { return load(configPath,profile) instanceof SessionProfile ? "security_token" : "api_key"; }
        catch (IOException e) { return "api_key"; }
    }

    static final class SessionProfile implements AuthenticationDetailsProvider, RegionProvider {
        private final Path tokenFile;
        private final Path keyFile;
        private final String fingerprint;
        private final String tenancy;
        private final String user;
        private final Region region;
        private final char[] passphrase;

        SessionProfile(ConfigFileReader.ConfigFile config,String configPath) throws IOException {
            tokenFile=resolve(config.get("security_token_file"),configPath);
            keyFile=resolve(required(config,"key_file"),configPath);
            fingerprint=required(config,"fingerprint");
            tenancy=required(config,"tenancy");
            region=Region.fromRegionCodeOrId(required(config,"region"));
            var pass=config.get("pass_phrase");
            passphrase=pass==null?null:pass.toCharArray();
            user=subject(token());
        }
        private static String required(ConfigFileReader.ConfigFile config,String key) {
            var value=config.get(key);
            if (value==null||value.isBlank()) throw new IllegalArgumentException("OCI profile is missing "+key);
            return value;
        }
        private static Path resolve(String value,String configPath) {
            var text=value.startsWith("~/")?System.getProperty("user.home")+value.substring(1):value;
            var path=Path.of(text);
            if (!path.isAbsolute()) path=Path.of(configPath).toAbsolutePath().getParent().resolve(path);
            return path.normalize();
        }
        String token() throws IOException {
            if (Files.size(tokenFile)>MAX_TOKEN) throw new IOException("session token file is too large");
            var token=Files.readString(tokenFile,StandardCharsets.US_ASCII).strip();
            if (token.isEmpty()||token.chars().anyMatch(ch->ch<=' ')) throw new IOException("session token file is not a token");
            return token;
        }
        static String subject(String token) throws IOException {
            var parts=token.split("\\.");
            if (parts.length!=3) throw new IOException("session token is not a JWT");
            try {
                var claims=JsonMapper.builder().build().readTree(Base64.getUrlDecoder().decode(parts[1]));
                var sub=claims.path("sub").asText("");
                if (!USER.matcher(sub).matches()) throw new IOException("session token has no user subject");
                return sub;
            } catch (IllegalArgumentException e) { throw new IOException("session token payload is not readable"); }
        }
        @Override public String getKeyId() {
            try { return "ST$"+token(); } catch (IOException e) { throw new IllegalStateException("OCI session token could not be read",e); }
        }
        @Override public InputStream getPrivateKey() {
            try { return new ByteArrayInputStream(Files.readAllBytes(keyFile)); }
            catch (IOException e) { throw new IllegalStateException("OCI session key could not be read",e); }
        }
        @Override @Deprecated public String getPassPhrase() { return passphrase==null?null:new String(passphrase); }
        @Override public char[] getPassphraseCharacters() { return passphrase==null?null:passphrase.clone(); }
        @Override public String getFingerprint() { return fingerprint; }
        @Override public String getTenantId() { return tenancy; }
        @Override public String getUserId() { return user; }
        @Override public Region getRegion() { return region; }
    }
}
