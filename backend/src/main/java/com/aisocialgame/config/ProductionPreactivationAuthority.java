package com.aisocialgame.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Verifies the target-projected signed authority before production B0 starts. */
final class ProductionPreactivationAuthority {

    static final Path AUTHORITY_ROOT = Path.of("/run/aienie/release-authority");
    private static final String COMPONENT = "ai-social-game";
    private static final String TARGET_PROFILE = "prod-products-68";
    private static final Set<String> FILES = Set.of(
            "production-release-manifest-v4.json",
            "production-publication-receipt.json",
            "production-publication-receipt.json.sig",
            "production-publication-public-key.pem",
            "production-preactivation-authority.json",
            "production-preactivation-authority.json.sig",
            "production-preactivation-authority-public-key.pem");
    private static final Set<String> PUBLICATION_KEYS = Set.of(
            "schema_version", "release_id", "canonical_component_id", "release_version",
            "source_commit", "artifact_sha256", "outer_manifest_sha256",
            "production_build_receipt_sha256", "production_build_signature_sha256",
            "registry_publication_receipt_sha256", "builder_installation_receipt_sha256",
            "offline_cache_receipt_sha256", "runtime_image_bindings_sha256",
            "runtime_image_evidence_sha256", "signing_key_sha256", "issued_at_utc");
    private static final Set<String> PREACTIVATION_KEYS = Set.of(
            "schema_version", "canonical_component_id", "release_id", "release_version",
            "candidate_root", "rollout_id", "rollout_plan_sha256", "execution_generation",
            "target_profile_id", "target_profile_revision", "target_revision",
            "target_snapshot_sha256", "approval_scope_sha256", "artifact_sha256",
            "outer_manifest_sha256", "publication_receipt_sha256",
            "publication_signature_sha256", "publication_signing_key_sha256",
            "preactivation_signing_key_sha256", "image_closure_sha256",
            "image_bindings_sha256", "config_overlay_sha256", "secret_bindings_sha256",
            "backup_closure_sha256", "backup_closure_signature_sha256", "action",
            "invocation_sha256", "journal_path", "issued_at_utc", "result");
    private static final Pattern DIGEST = Pattern.compile("sha256:(?!0{64})[0-9a-f]{64}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final byte[] ED25519_PKIX_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private ProductionPreactivationAuthority() {
    }

    static void requireAuthorized() {
        verifyAt(AUTHORITY_ROOT, true);
    }

    static void verifyAt(Path authorityRoot, boolean requireRootOwnership) {
        try {
            Path root = authorityRoot.toAbsolutePath().normalize();
            if (!root.equals(authorityRoot.toAbsolutePath()) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                throw new IllegalStateException("production release authority root is invalid");
            }
            Set<String> observed = new TreeSet<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) {
                    observed.add(entry.getFileName().toString());
                }
            }
            if (!observed.equals(new TreeSet<>(FILES))) {
                throw new IllegalStateException("production release authority file closure drifted");
            }

            byte[] outerRaw = readFile(root.resolve("production-release-manifest-v4.json"), requireRootOwnership);
            byte[] publicationRaw = readFile(root.resolve("production-publication-receipt.json"), requireRootOwnership);
            byte[] publicationSignature = readFile(
                    root.resolve("production-publication-receipt.json.sig"), requireRootOwnership);
            KeyEvidence publicationKey = readKey(
                    root.resolve("production-publication-public-key.pem"), requireRootOwnership);
            byte[] preactivationRaw = readFile(
                    root.resolve("production-preactivation-authority.json"), requireRootOwnership);
            byte[] preactivationSignature = readFile(
                    root.resolve("production-preactivation-authority.json.sig"), requireRootOwnership);
            KeyEvidence preactivationKey = readKey(
                    root.resolve("production-preactivation-authority-public-key.pem"), requireRootOwnership);

            JsonNode outer = canonicalObject(outerRaw, null);
            JsonNode publication = canonicalObject(publicationRaw, PUBLICATION_KEYS);
            JsonNode preactivation = canonicalObject(preactivationRaw, PREACTIVATION_KEYS);
            verifySignature(publicationKey.key(), publicationRaw, publicationSignature);
            verifySignature(preactivationKey.key(), preactivationRaw, preactivationSignature);
            validateOuterAndPublication(
                    outer, outerRaw, publication, publicationRaw, publicationSignature, publicationKey);
            validatePreactivation(
                    outer, outerRaw, publication, publicationRaw, publicationSignature,
                    publicationKey, preactivation, preactivationKey);
        } catch (IOException | java.security.GeneralSecurityException error) {
            throw new IllegalStateException("production release authority verification failed", error);
        }
    }

    private static void validateOuterAndPublication(
            JsonNode outer,
            byte[] outerRaw,
            JsonNode publication,
            byte[] publicationRaw,
            byte[] publicationSignature,
            KeyEvidence publicationKey
    ) {
        JsonNode target = requiredObject(outer, "target");
        requireText(outer, "schema_version", "v4");
        requireText(outer, "environment", "production");
        requireText(outer, "operation", "release");
        requireText(outer, "canonical_component_id", COMPONENT);
        requireText(outer, "component", COMPONENT);
        requireText(outer, "project_key", COMPONENT);
        requireIdentifier(outer, "release_id");
        requireIdentifier(outer, "release_version");
        requireIdentifier(outer, "rollout_id");
        requireText(target, "target_profile_id", TARGET_PROFILE);
        requirePositiveInteger(target, "target_profile_revision");
        requireDigest(target, "target_revision");
        requireDigest(target, "snapshot_sha256");

        requireText(publication, "schema_version", "aienie-production-publication-receipt-v1");
        requireText(publication, "canonical_component_id", COMPONENT);
        requireEqual(publication, "release_id", outer, "release_id");
        requireEqual(publication, "release_version", outer, "release_version");
        requireEqual(publication, "source_commit", outer, "source_commit");
        requireText(publication, "outer_manifest_sha256", sha256(outerRaw));
        requireText(publication, "runtime_image_bindings_sha256", requiredDigest(outer, "image_bindings_digest"));
        requireText(publication, "runtime_image_evidence_sha256", requiredDigest(outer, "image_evidence_digest"));
        requireText(publication, "signing_key_sha256", publicationKey.rawKeySHA256());
        for (String key : List.of(
                "artifact_sha256", "outer_manifest_sha256", "production_build_receipt_sha256",
                "production_build_signature_sha256", "registry_publication_receipt_sha256",
                "builder_installation_receipt_sha256", "offline_cache_receipt_sha256",
                "runtime_image_bindings_sha256", "runtime_image_evidence_sha256", "signing_key_sha256")) {
            requireDigest(publication, key);
        }
        requireTimestamp(publication, "issued_at_utc");
        if (publicationRaw.length == 0 || publicationSignature.length != 64) {
            throw new IllegalStateException("production publication evidence is empty");
        }
    }

    private static void validatePreactivation(
            JsonNode outer,
            byte[] outerRaw,
            JsonNode publication,
            byte[] publicationRaw,
            byte[] publicationSignature,
            KeyEvidence publicationKey,
            JsonNode preactivation,
            KeyEvidence preactivationKey
    ) {
        JsonNode target = requiredObject(outer, "target");
        requireText(preactivation, "schema_version", "aienie-production-preactivation-authority-v1");
        requireText(preactivation, "canonical_component_id", COMPONENT);
        requireEqual(preactivation, "release_id", outer, "release_id");
        requireEqual(preactivation, "release_version", outer, "release_version");
        requireEqual(preactivation, "rollout_id", outer, "rollout_id");
        requireText(preactivation, "target_profile_id", TARGET_PROFILE);
        requireEqual(preactivation, "target_profile_revision", target, "target_profile_revision");
        requireEqual(preactivation, "target_revision", target, "target_revision");
        requireEqual(preactivation, "target_snapshot_sha256", target, "snapshot_sha256");
        requireText(preactivation, "artifact_sha256", requiredDigest(publication, "artifact_sha256"));
        requireText(preactivation, "outer_manifest_sha256", sha256(outerRaw));
        requireText(preactivation, "publication_receipt_sha256", sha256(publicationRaw));
        requireText(preactivation, "publication_signature_sha256", sha256(publicationSignature));
        requireText(preactivation, "publication_signing_key_sha256", publicationKey.rawKeySHA256());
        requireText(preactivation, "preactivation_signing_key_sha256", preactivationKey.rawKeySHA256());
        requireText(preactivation, "image_closure_sha256", requiredDigest(outer, "image_closure_digest"));
        requireText(preactivation, "image_bindings_sha256", requiredDigest(outer, "image_bindings_digest"));
        requireText(preactivation, "config_overlay_sha256", requiredDigest(outer, "config_overlay_digest"));
        requireText(preactivation, "secret_bindings_sha256", requiredDigest(outer, "production_secret_bindings_digest"));
        requireText(preactivation, "action", "authorize-compose-start");
        requireText(preactivation, "result", "authorized");
        long generation = requirePositiveInteger(preactivation, "execution_generation");
        String version = requiredText(preactivation, "release_version");
        String rollout = requiredText(preactivation, "rollout_id");
        String invocation = requiredDigest(preactivation, "invocation_sha256");
        requireText(preactivation, "candidate_root",
                "/srv/aienie-products/" + COMPONENT + "/.aienie-releases/" + version);
        requireText(preactivation, "journal_path",
                "/var/lib/aienie-production/product-preactivation/" + COMPONENT + "/" + rollout
                        + "/" + generation + "/" + invocation + "/receipt.json");
        for (String key : List.of(
                "rollout_plan_sha256", "target_revision", "target_snapshot_sha256",
                "approval_scope_sha256", "artifact_sha256", "outer_manifest_sha256",
                "publication_receipt_sha256", "publication_signature_sha256",
                "publication_signing_key_sha256", "preactivation_signing_key_sha256",
                "image_closure_sha256", "image_bindings_sha256", "config_overlay_sha256",
                "secret_bindings_sha256", "backup_closure_sha256",
                "backup_closure_signature_sha256", "invocation_sha256")) {
            requireDigest(preactivation, key);
        }
        requireTimestamp(preactivation, "issued_at_utc");
    }

    private static byte[] readFile(Path path, boolean requireRootOwnership) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.equals(path.toAbsolutePath()) || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("production release authority file is unsafe");
        }
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (!permissions.equals(Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ))) {
            throw new IllegalStateException("production release authority file mode is invalid");
        }
        if (requireRootOwnership) {
            Object uid = Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS);
            if (!(uid instanceof Number) || ((Number) uid).longValue() != 0L) {
                throw new IllegalStateException("production release authority file owner is invalid");
            }
        }
        byte[] raw = Files.readAllBytes(path);
        if (raw.length == 0 || raw.length > 2 * 1024 * 1024) {
            throw new IllegalStateException("production release authority file size is invalid");
        }
        return raw;
    }

    private static KeyEvidence readKey(Path path, boolean requireRootOwnership)
            throws IOException, java.security.GeneralSecurityException {
        byte[] raw = readFile(path, requireRootOwnership);
        String text = new String(raw, StandardCharsets.US_ASCII);
        String header = "-----BEGIN PUBLIC KEY-----\n";
        String footer = "\n-----END PUBLIC KEY-----\n";
        if (!text.startsWith(header) || !text.endsWith(footer)) {
            throw new IllegalStateException("production release authority key is not canonical PKIX PEM");
        }
        String encoded = text.substring(header.length(), text.length() - footer.length());
        byte[] der;
        try {
            der = Base64.getMimeDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("production release authority key is not base64", error);
        }
        String canonical = header + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der) + footer;
        if (!canonical.equals(text) || der.length != ED25519_PKIX_PREFIX.length + 32) {
            throw new IllegalStateException("production release authority key encoding drifted");
        }
        for (int index = 0; index < ED25519_PKIX_PREFIX.length; index++) {
            if (der[index] != ED25519_PKIX_PREFIX[index]) {
                throw new IllegalStateException("production release authority key algorithm drifted");
            }
        }
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        byte[] rawKey = java.util.Arrays.copyOfRange(der, ED25519_PKIX_PREFIX.length, der.length);
        return new KeyEvidence(key, sha256(rawKey));
    }

    private static void verifySignature(PublicKey key, byte[] payload, byte[] signature)
            throws java.security.GeneralSecurityException {
        if (signature.length != 64) {
            throw new IllegalStateException("production release authority signature size is invalid");
        }
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(payload);
        if (!verifier.verify(signature)) {
            throw new IllegalStateException("production release authority signature is invalid");
        }
    }

    private static JsonNode canonicalObject(byte[] raw, Set<String> expectedFields) throws IOException {
        JsonNode value = JSON.readTree(raw);
        if (value == null || !value.isObject() || !java.util.Arrays.equals(raw, canonical(value))) {
            throw new IllegalStateException("production release authority JSON is not canonical");
        }
        if (expectedFields != null) {
            Set<String> observed = new TreeSet<>();
            value.fieldNames().forEachRemaining(observed::add);
            if (!observed.equals(new TreeSet<>(expectedFields))) {
                throw new IllegalStateException("production release authority JSON field closure drifted");
            }
        }
        return value;
    }

    static byte[] canonical(JsonNode value) throws IOException {
        StringBuilder output = new StringBuilder();
        writeCanonical(value, output);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeCanonical(JsonNode value, StringBuilder output) throws IOException {
        if (value.isObject()) {
            output.append('{');
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                String name = names.get(index);
                output.append(JSON.writeValueAsString(name)).append(':');
                writeCanonical(value.get(name), output);
            }
            output.append('}');
        } else if (value.isArray()) {
            output.append('[');
            for (int index = 0; index < value.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                writeCanonical(value.get(index), output);
            }
            output.append(']');
        } else if (value.isTextual()) {
            output.append(JSON.writeValueAsString(value.textValue()));
        } else if (value.isIntegralNumber()) {
            BigInteger integer = value.bigIntegerValue();
            output.append(integer);
        } else if (value.isBoolean()) {
            output.append(value.booleanValue());
        } else if (value.isNull()) {
            output.append("null");
        } else {
            throw new IllegalStateException("production release authority JSON number type is forbidden");
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String key) {
        JsonNode value = parent.get(key);
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("production release authority object field is invalid");
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String key) {
        JsonNode value = parent.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("production release authority text field is invalid");
        }
        return value.textValue();
    }

    private static void requireText(JsonNode parent, String key, String expected) {
        if (!expected.equals(requiredText(parent, key))) {
            throw new IllegalStateException("production release authority identity drifted");
        }
    }

    private static void requireEqual(JsonNode left, String leftKey, JsonNode right, String rightKey) {
        JsonNode leftValue = left.get(leftKey);
        JsonNode rightValue = right.get(rightKey);
        if (leftValue == null || rightValue == null || !leftValue.equals(rightValue)) {
            throw new IllegalStateException("production release authority cross-binding drifted");
        }
    }

    private static void requireIdentifier(JsonNode parent, String key) {
        if (!IDENTIFIER.matcher(requiredText(parent, key)).matches()) {
            throw new IllegalStateException("production release authority identifier is invalid");
        }
    }

    private static String requiredDigest(JsonNode parent, String key) {
        String value = requiredText(parent, key);
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalStateException("production release authority digest is invalid");
        }
        return value;
    }

    private static void requireDigest(JsonNode parent, String key) {
        requiredDigest(parent, key);
    }

    private static long requirePositiveInteger(JsonNode parent, String key) {
        JsonNode value = parent.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1) {
            throw new IllegalStateException("production release authority generation is invalid");
        }
        return value.longValue();
    }

    private static void requireTimestamp(JsonNode parent, String key) {
        String raw = requiredText(parent, key);
        try {
            Instant instant = Instant.parse(raw);
            if (!raw.endsWith("Z") || instant.isAfter(Instant.now().plusSeconds(60))) {
                throw new IllegalStateException("production release authority timestamp is invalid");
            }
        } catch (DateTimeParseException error) {
            throw new IllegalStateException("production release authority timestamp is invalid", error);
        }
    }

    private static String sha256(byte[] raw) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private record KeyEvidence(PublicKey key, String rawKeySHA256) {
    }
}
